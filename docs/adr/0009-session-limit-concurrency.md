# ADR-0009: Session-Limit Concurrency — Pessimistic Lock

**Status:** Accepted
**Sana:** 2026-08-15
**Qamrov:** `finbank-auth-service` (auth-service tech spec §8-bo'limida rejalashtirilgan qaror)

## Kontekst

Auth Service Texnik Topshiriq'ning 7-bo'limi (#4) va 8-bo'limi bu muammoni oldindan
belgilagan edi: bir xil user'dan bir vaqtning o'zida 6 (yoki undan ko'p) parallel login
so'rovi kelsa, har biri mustaqil "ACTIVE session'lar soni 5 dan kam, demak eviction kerak
emas" deb hisoblashi mumkin — natijada barchasi session yaratadi va 5 device limiti buziladi
(classic lost update, 8-bo'limdagi "Balance 700, ikki debit 500" misoliga o'xshash).

Tech spec bu qarorni "implementatsiya bosqichida kod yozib, test bilan isbotlab tanlanadi"
deb ochiq qoldirgan edi (concept → experiment → apply → failure test → xulosa). Bu ADR o'sha
tajriba natijasini rasmiylashtiradi.

## Ko'rib chiqilgan variantlar

Tech spec §8'da sanab o'tilgan uchta variant sinovdan o'tkazildi:

**A — `SELECT ... FOR UPDATE` (pessimistic lock) user qatoriga.** Login paytida user
qatori lock qilinadi, shu lock ostida session soni hisoblanadi va eviction bajariladi.

**B — Session yaratilgandan keyin alohida "cleanup" query.** Window function bilan
(`DELETE ... WHERE rank > 5`), lekin bu ham lock'siz bo'lsa xuddi shu race'ga moyil.

**C — Redis'da atomik counter (`INCR` + Lua script).** Tezkor, lekin PostgreSQL
(source of truth, sessions jadvali) bilan sinxronlashtirish zarurati qo'shadi — ikkita
alohida ma'lumot ombori orasida yangi konsistentlik muammosi yaratadi.

## Qaror

**Variant A** qabul qilindi: `UserRepository.lockById(userId)` — `SELECT ... FOR UPDATE`
ekvivalenti (`@Lock(LockModeType.PESSIMISTIC_WRITE)`), login flow'ida yangi session
yaratishdan va eviction tekshiruvidan **oldin** chaqiriladi.

### Nega A

- **Bitta ma'lumot ombori, bitta lock** — B va C'dagi kabi qo'shimcha sinxronlashtirish
  qatlami yoki race-prone ikkinchi query kerak emas.
- PostgreSQL'ning o'zi transaction chegarasida lock'ni avtomatik bo'shatadi (commit/rollback'da)
  — qo'shimcha cleanup logikasi yozish shart emas.
- Login — yuqori chastotali emas (bitta user uchun bir vaqtda bir nechta login odatiy emas,
  faqat maxsus stsenariy — masalan bir nechta device'dan bir vaqtda kirish), shuning uchun
  lock'ning qisqa muddatli performance narxi (boshqa login so'rovlari shu user uchun navbatda
  kutadi) amaliy jihatdan sezilmaydi.

### Nega B va C emas

- B — window function bilan "tozalash" query'si ham, agar ikkita parallel so'rov session'ni
  bir vaqtda yozib, keyin bir vaqtda "tozalash"ni ishga tushirsa, bir xil race'ga qaytadi —
  faqat oynani kichraytiradi, yo'q qilmaydi.
- C — Redis counter tezkor, lekin PostgreSQL'dagi haqiqiy session qatorlari bilan sinxron
  emas (masalan Redis counter yangilanib, keyin DB yozish muvaffaqiyatsiz bo'lsa — ikkalasi
  farqlanib qoladi). Bitta manba (PostgreSQL, tech-spec §3'dagi "Redis — faqat lookup cache"
  tamoyiliga mos) ustida lock ishlatish soddaroq va ishonchli.

### Isbotlash

`SessionLimitConcurrencyIntegrationTest` — 8 ta parallel login so'rovi (`ExecutorService` +
`CountDownLatch`, real Testcontainers Postgres/Redis/Kafka'ga qarshi):

- **Lock'siz**: 3/3 marta muvaffaqiyatsiz — 5 ta kutilgan session o'rniga 8 tasi ACTIVE
  bo'lib qoldi (`expected: 5 but was: 8`).
- **Lock bilan**: 100% barqaror — har doim aynan 5 ta ACTIVE session.

## Amalga oshirish

- `UserRepository.lockById(String id)` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` bilan.
- `AuthServiceImpl.login()` — yangi session yaratish va `evictOldestSessionIfLimitReached()`
  chaqirilishidan oldin `userRepository.lockById(user.getId())` chaqiriladi, shu orqali
  butun "hisobla + yoz" operatsiyasi bitta transaction ichida atomik bo'ladi.

## Trade-off

- Bir xil user'ga bir vaqtda kelgan login so'rovlari endi **ketma-ket** (serial) bajariladi,
  parallel emas — lekin bu faqat bitta user'ning session-limit tekshiruvi uchun, boshqa
  user'larning login so'rovlariga ta'sir qilmaydi (lock faqat shu user qatoriga qo'yiladi).
- Agar kelajakda login RPS juda oshib, bitta user'dan haqiqatda yuqori concurrent login
  bo'lishi kutilsa (masalan bot/load-test stsenariysi), bu lock navbat hosil qilishi mumkin —
  hozirgi profil (odatiy foydalanuvchi xatti-harakati) uchun bu xavf past.

## Natija

Auth Service'ning session-limit eviction'i pessimistic row lock orqali atomik. Tech
spec §12'dagi Definition of Done'ning oxirgi bandi ("concurrency yechimi ADR sifatida
yozilgan") shu ADR bilan yopiladi.

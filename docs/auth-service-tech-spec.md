# Auth Service — Texnik Topshiriq

**Repo:** `finbank-auth-service`
**Sprint:** 1
**Status:** Implemented — Sprint 1 skope'i to'liq bajarilgan (12-bo'limdagi Definition of
Done'ga qarang). Quyidagi bo'limlar endi tarixiy reja emas, amalga oshirilgan holatning
tavsifi sifatida o'qilishi kerak.

> **Eslatma (post-implementation):** endpoint yo'llari pastda `/api/v1/auth/...` prefiksi
> bilan yozilgan, lekin implementatsiya paytida bu prefiks **olib tashlangan** — haqiqiy
> yo'llar prefiksiz (`/register`, `/login`, `/refresh` va h.k.), `CLAUDE.md` va
> `README.md`dagidek. Sabab: bitta service ichida versiyalash uchun alohida prefiks keraksiz
> murakkablik qo'shadi — versiyalash kerak bo'lsa, API Gateway darajasida hal qilinadi.

---

## 1. Maqsad va Scope

Auth Service — barcha foydalanuvchi turlari (CUSTOMER, OPERATOR, ADMIN, AUDITOR) uchun
autentifikatsiya va session boshqaruvini ta'minlaydi. Bu service **avtorizatsiya
qarorlarini** (kim nimani qila oladi) qabul qilmaydi — faqat "sen kimsan" va "sening
token'ing haqiqiymi"ni tasdiqlaydi. Har bir keyingi service o'zining RBAC qoidalarini
JWT ichidagi `role` claim asosida o'zi tekshiradi.

### Scope ichida (Sprint 1)
- Register (faqat CUSTOMER uchun self-service; STAFF rollar admin tomonidan yaratiladi)
- Login / Logout / Logout-all
- Access + Refresh token, refresh token rotation
- Session (device) boshqaruvi — ro'yxat, revoke, LRU eviction (max 5)
- Password reset (request + confirm)
- Brute-force himoya (rate limiting + account lockout)

### Scope tashqarisida (keyingi sprint)
- OAuth/SSO (Google/Apple login)
- MFA/2FA (Sprint 5, fraud-service bilan birga qaraladi)
- Email verification flow (hozircha registratsiyada email tasdiqlanmagan holda ishlaydi)

---

## 2. Actor'lar va Rollar

| Role | Kim yaratadi | Login qilish usuli |
|---|---|---|
| CUSTOMER | Self-service (`POST /register`) | email + parol |
| OPERATOR | ADMIN tomonidan yaratiladi | email + parol |
| ADMIN | Seed/migration orqali (birinchi admin), keyingilarini ADMIN yaratadi | email + parol |
| AUDITOR | ADMIN tomonidan yaratiladi | email + parol |

> **Muhim qaror:** STAFF (OPERATOR/ADMIN/AUDITOR) uchun `POST /register` endpoint orqali
> ro'yxatdan o'tish **YO'Q**. Bu endpoint faqat CUSTOMER uchun ochiq. Aks holda istalgan
> kishi o'zini ADMIN qilib ro'yxatdan o'tkazishi mumkin bo'lardi — bu jiddiy security hole.
> STAFF yaratish uchun alohida ichki endpoint (`POST /internal/staff`, faqat ADMIN roli
> chaqira oladi) Sprint 1'da skeleton darajasida qo'shiladi.

---

## 3. Data Model

### `users` jadvali
```
id              UUID PK
email           VARCHAR UNIQUE NOT NULL
password_hash   VARCHAR NOT NULL        -- Argon2id
role            ENUM(CUSTOMER, OPERATOR, ADMIN, AUDITOR) NOT NULL
status          ENUM(ACTIVE, LOCKED, DISABLED) NOT NULL DEFAULT ACTIVE
failed_login_count   INT NOT NULL DEFAULT 0
locked_until    TIMESTAMP NULL
created_at      TIMESTAMP NOT NULL
updated_at      TIMESTAMP NOT NULL
```

### `sessions` jadvali (refresh token metadata)
```
id              UUID PK
user_id         UUID FK -> users.id
refresh_token_hash  VARCHAR NOT NULL   -- SHA-256, xom token hech qachon saqlanmaydi
device_label    VARCHAR              -- "iPhone 15 · Chrome" kabi, User-Agent'dan parse
ip_address      VARCHAR
status          ENUM(ACTIVE, REVOKED, EXPIRED) NOT NULL DEFAULT ACTIVE
created_at      TIMESTAMP NOT NULL
last_used_at    TIMESTAMP NOT NULL
expires_at      TIMESTAMP NOT NULL     -- created_at + 7 kun
```
> Nega Redis emas, PostgreSQL? Session ma'lumoti **audit uchun** kerak (kim qachon qayerdan
> kirgan) — bu yo'qolmasligi kerak bo'lgan ma'lumot, Redis esa TTL bilan ishlaydi va
> restart'da yo'qolishi mumkin. Redis'ni faqat **tezkor lookup cache** sifatida (refresh
> token hash → user_id) ustiga qo'shamiz, source of truth — PostgreSQL.

### `password_reset_tokens` jadvali
```
id              UUID PK
user_id         UUID FK -> users.id
token_hash      VARCHAR NOT NULL
status          ENUM(PENDING, USED, EXPIRED) NOT NULL DEFAULT PENDING
created_at      TIMESTAMP NOT NULL
expires_at      TIMESTAMP NOT NULL     -- created_at + 15 daqiqa
```

---

## 4. Token Strategiyasi

- **Access token** — JWT, TTL = 15 daqiqa. Claim'lar: `sub` (user_id), `role`, `session_id`, `iat`, `exp`.
- **Refresh token** — kriptografik random 256-bit qiymat (JWT emas — chunki tekshirish
  faqat DB/Redis orqali bo'ladi, revoke qilish imkoni bo'lishi kerak). TTL = 7 kun.
- **Rotation:** har `POST /refresh` chaqirilganda — eski refresh token **REVOKED** bo'ladi,
  yangisi qaytariladi. Bu "refresh token reuse detection" uchun asos (pastda ko'ring).

---

## 5. Session Limit — 5 device, LRU eviction

**Qoida:** har bir user uchun bir vaqtning o'zida maksimal **5 ta ACTIVE session**.

**Login vaqtida:**
1. Yangi session yaratiladi (status=ACTIVE)
2. Agar user'ning ACTIVE session'lari soni > 5 bo'lsa → eng kam ishlatilgan
   (`last_used_at` bo'yicha eng eski) session **avtomatik REVOKED** qilinadi
3. Bu operatsiya **atomik** bo'lishi kerak (concurrency muammosi — pastga qarang)

**Foydalanuvchi tomonidan:**
- `GET /sessions` — barcha ACTIVE session'lar ro'yxati (device_label, ip, last_used_at)
- `DELETE /sessions/{id}` — muayyan session'ni revoke qilish ("Bu men emasman" tugmasi)
- `POST /logout-all` — barcha session'larni revoke qilish (parol o'zgarganda avtomatik chaqiriladi)

---

## 6. Asosiy Flow'lar (Happy Path)

### 6.1 Register
```
POST /register
{ email, password }
->
Validate (email format, parol kuchi: min 8 belgi, kamida 1 raqam)
Email unique tekshirish
Argon2id bilan hash
users jadvaliga yozish (role=CUSTOMER, status=ACTIVE)
Kafka: UserRegistered event -> customer-service KYC profil yaratadi
201 Created
```

### 6.2 Login
```
POST /login
{ email, password }
->
User email bo'yicha topiladi
status == LOCKED/DISABLED bo'lsa -> 423 Locked
Parol tekshiriladi (Argon2id verify)
Muvaffaqiyatli bo'lsa: failed_login_count = 0
Session yaratiladi, LRU eviction tekshiriladi (5 dan oshsa)
Access token (JWT) + Refresh token qaytariladi
Kafka: LoginSucceeded event -> fraud-service uchun (unusual location/device tekshirish)
200 OK { access_token, refresh_token, expires_in }
```

### 6.3 Refresh
```
POST /refresh
{ refresh_token }
->
Hash hisoblanadi, sessions jadvalidan topiladi
status != ACTIVE -> 401 (pastga qarang: reuse detection)
expires_at < now -> 401 Expired
Eski session REVOKED, yangi session yaratiladi (rotation)
Yangi access + refresh token qaytariladi
```

### 6.4 Password Reset
```
POST /password-reset/request { email }
-> Token yaratiladi (15 daqiqa TTL), Kafka: PasswordResetRequested -> notification-service email yuboradi
-> 200 OK (email mavjud/mavjud emasligini bildirmaydi -- enumeration attack himoyasi)

POST /password-reset/confirm { token, new_password }
-> Token tekshiriladi (PENDING, muddati o'tmagan)
-> Parol yangilanadi, token USED qilinadi
-> BARCHA session'lar logout-all (xavfsizlik: agar parol kompromis bo'lgan bo'lsa, eski session'lar ham yopiladi)
-> Kafka: PasswordChanged event
```

---

## 7. Edge Case va Failure Scenario'lar

| # | Stsenariy | Kutilgan xatti-harakat |
|---|---|---|
| 1 | Noto'g'ri parol 5 marta ketma-ket | `failed_login_count` oshiriladi; 5-chi urinishda `status=LOCKED`, `locked_until = now+30min` |
| 2 | LOCKED user login qilishga urinadi | 423 Locked, qolgan vaqt ko'rsatiladi |
| 3 | Refresh token 2 marta ishlatiladi (o'g'irlangan token reuse) | Birinchi marta ishlatilgach REVOKED bo'lgan; ikkinchi urinish 401 qaytaradi **VA** shu user'ning **barcha** session'lari avtomatik revoke qilinadi (potentsial token theft signal) + Kafka: `SuspiciousTokenReuse` event -> fraud-service |
| 4 | Bir xil user, 6 ta device'dan bir vaqtda login (race condition) | Session yaratish + eviction — bitta DB transaction ichida, `SELECT ... FOR UPDATE` bilan user'ning session count'ini lock qilib hisoblash kerak (aks holda 6 ta ham ACTIVE bo'lib qolishi mumkin — classic lost update) |
| 5 | Register — email allaqachon mavjud | 409 Conflict, aniq xabar bermaslik ("bu email band" o'rniga umumiy — enumeration'ni kamaytirish uchun ixtiyoriy) |
| 6 | Register — 2 marta bir xil so'rov (idempotency) | Unique constraint email'da — ikkinchisi 409 qaytaradi, side-effect yo'q |
| 7 | Password reset token muddati o'tgan | 410 Gone yoki 400, aniq xabar |
| 8 | Password reset token allaqachon ishlatilgan (USED) | 400 — qayta ishlatib bo'lmaydi |
| 9 | Access token muddati o'tgan, lekin refresh qilinmagan | Downstream service 401 qaytaradi, client refresh flow'ni ishga tushiradi |
| 10 | STAFF (OPERATOR/ADMIN) login — CUSTOMER bilan bir xil flow, farq yo'q, lekin `LoginSucceeded` event'ida `role` STAFF bo'lsa fraud-service kuchliroq monitoring qiladi (masalan har doim MFA talab qilinishi kelajakda) |
| 11 | Kafka mavjud emas / notification yuborilmadi (password reset) | Auth Service o'zi **fail bo'lmasligi kerak** — token baribir yaratiladi, DB'ga yoziladi; Kafka publish outbox pattern orqali retry qilinadi (Sprint 3-4'dagi Outbox pattern bilan bog'lanadi — hozircha Sprint 1'da oddiy best-effort publish, TODO qoldiriladi) |

---

## 8. Concurrency Nuqtasi (o'rganish uchun muhim)

Stsenariy #4 — bu loyihaning "Concurrency — asosiy learning track" bo'limiga bevosita
bog'liq. Agar 6 ta parallel login request kelsa va har biri "session count = 4, demak
eviction kerak emas" deb noto'g'ri hisoblasa — 6 ta ham ACTIVE bo'lib qoladi (lost update,
xuddi "Balance 700, ikki debit 500" misoli kabi).

**Yechim variantlari (implementatsiya bosqichida sinab ko'riladi):**
- `SELECT COUNT(*) ... FOR UPDATE` user_id bo'yicha lock bilan
- Yoki: session yaratilgandan keyin, alohida "cleanup" query — `DELETE eng eski session
  WHERE user_id=X AND rank > 5` (window function bilan), bu ham race'ga moyil bo'lishi mumkin
- Yoki: Redis'da atomik counter (`INCR` + Lua script) — lekin bu holda PostgreSQL source
  of truth bilan sync qilish kerak

Bu qarorni **implementatsiya paytida**, kod yozib, test bilan isbotlab tanlaymiz (sizning
workflow'ingizga mos: concept → experiment → apply → failure test → xulosa).

---

## 9. Xavfsizlik Talablari

- Parollar faqat **Argon2id** bilan hash qilinadi (bcrypt emas — Argon2 zamonaviy standart)
- Refresh token DB'da faqat **hash** ko'rinishda saqlanadi
- Rate limiting (Redis, fixed-window): `/login` — bir email'dan 10 so'rov/5 daqiqa, bir
  IP'dan 30 so'rov/5 daqiqa; `/password-reset/request` — bir email'dan 3 so'rov/15 daqiqa,
  bir IP'dan 10 so'rov/15 daqiqa. Limit oshsa — 429 Too Many Requests.
  (`app.security.rate-limit.*`, `application.yaml`)
- Barcha auth event'lar (login success/fail, password change, session revoke) audit log'ga yoziladi
- Password reset so'rovi email mavjudligini oshkor qilmaydi (enumeration himoyasi)

---

## 10. API Ro'yxati (Sprint 1)

| Endpoint | Auth kerak? | Tavsif |
|---|---|---|
| `POST /register` | Yo'q | CUSTOMER self-register |
| `POST /login` | Yo'q | Login, token qaytaradi |
| `POST /refresh` | Yo'q (refresh token orqali) | Token rotation |
| `POST /logout` | Ha | Joriy session'ni revoke |
| `POST /logout-all` | Ha | Barcha session'larni revoke |
| `GET /sessions` | Ha | Faol session'lar ro'yxati |
| `DELETE /sessions/{id}` | Ha | Muayyan session'ni revoke |
| `POST /password-reset/request` | Yo'q | Reset token so'rash |
| `POST /password-reset/confirm` | Yo'q (token orqali) | Yangi parol qo'yish |
| `POST /internal/staff` | Ha (faqat ADMIN) | STAFF user yaratish — skeleton, to'liq emas |

---

## 11. Kafka Event'lar (Producer)

| Event | Qachon | Kim consume qiladi |
|---|---|---|
| `UserRegistered` | Register muvaffaqiyatli | customer-service |
| `LoginSucceeded` | Login muvaffaqiyatli | fraud-service, reporting-service |
| `LoginFailed` | Login muvaffaqiyatsiz | fraud-service (brute-force pattern) |
| `SuspiciousTokenReuse` | Refresh token reuse aniqlangan | fraud-service |
| `PasswordChanged` | Parol muvaffaqiyatli o'zgartirilgan | notification-service |
| `PasswordResetRequested` | Reset so'rovi | notification-service |

> Sprint 1'da bu event'lar **oddiy best-effort publish** bilan yoziladi (to'liq Outbox
> pattern Sprint 3-4'da qo'shiladi). Bu ham hujjatlashtirilgan texnik qarz (tech debt) —
> keyingi sprintga aniq belgilab qo'yiladi.

---

## 12. Definition of Done (shu service uchun)

- [x] Barcha 10 endpoint ishlaydi va happy path test qilingan
- [x] 11-bo'limdagi barcha failure/edge case test qilingan (ayniqsa #3 va #4)
- [x] Concurrency stsenariysi (#4) uchun maxsus integration test yozilgan (parallel thread
  bilan) — `SessionLimitConcurrencyIntegrationTest`, pessimistic lock (`UserRepository.lockById`)
- [x] Argon2id, rate limiting, audit log ishlayapti — audit log alohida jadval sifatida emas,
  Kafka event'lari (11-bo'lim) orqali amalga oshirilgan
- [x] Testcontainers bilan PostgreSQL + Redis integration test (Kafka ham qo'shilgan)
- [x] README: qanday local run qilish, ADR-0001 va ADR-0002 (agar kerak bo'lsa) ga link
- [x] Bu spec'dagi 8-bo'limda qabul qilingan concurrency yechimi ADR sifatida yozilgan
  — [`docs/adr/0009-session-limit-concurrency.md`](./adr/0009-session-limit-concurrency.md)

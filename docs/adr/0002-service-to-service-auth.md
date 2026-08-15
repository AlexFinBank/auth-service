# ADR-0002: Service-to-Service Autentifikatsiya

**Status:** Accepted
**Sana:** 2026-08-15
**Qamrov:** Barcha FinBank microservice'lari (platform-wide qaror)

## Kontekst

API Gateway kirish paytida foydalanuvchi JWT'sini tasdiqlaydi. Lekin bitta
foydalanuvchi so'rovi ko'pincha bir nechta service orqali o'tadi — masalan:

```
Client -> Gateway -> transaction-service -> ledger-service
                                          -> fraud-service
```

Savol: `ledger-service` yoki `fraud-service` bunday ichki chaqiruv haqiqiy
foydalanuvchidan (Gateway orqali) kelayotganini qanday biladi? Bu tekshiruv
bo'lmasa, ichki tarmoqqa kirgan istalgan kod o'zini istalgan foydalanuvchi
(hatto ADMIN) qilib ko'rsatishi mumkin.

## Ko'rib chiqilgan variantlar

**A — Trust boundary + oddiy header (`X-User-Id`, `X-User-Role`).**
Gateway JWT'ni tekshiradi, ichkariga oddiy header sifatida uzatadi, downstream
service'lar ko'r-ko'rona ishonadi. Xavfsizlik butunlay tarmoq segmentatsiyasiga
(K8s NetworkPolicy, service mesh) bog'liq bo'ladi. Loyiha hozircha Docker
Compose bosqichida (K8s — roadmap'da 8-bosqich, hali yo'q), shuning uchun bu
trust boundary'ni ta'minlovchi infratuzilma mavjud emas — bu variant hozirgi
holatda xavfli.

**B — Original JWT'ni har bir service'ga o'zgartirmasdan uzatish, har biri
mustaqil tekshiradi.** Gateway JWT'ni tasdiqlaydi va `Authorization: Bearer`
header orqali o'zgarishsiz uzatadi. Har bir downstream service xuddi Gateway
kabi, JWT imzosini umumiy public key/secret orqali mustaqil tekshiradi.

**C — mTLS + service mesh (Istio/SPIFFE).** Har bir service transport
darajasida kriptografik "shaxs"ga ega bo'ladi (service identity). User identity
uchun emas — bu B bilan birga ishlatilishi kerak bo'lgan qo'shimcha qatlam.
Sozlash murakkabligi (Istio/service mesh) hozirgi loyiha bosqichiga mos emas.

## Qaror

**Variant B** qabul qilindi: original JWT har bir service'ga o'zgarishsiz
uzatiladi, har bir service uni mustaqil ravishda tekshiradi.

### Nega B

- Xavfsizlik **kriptografiyaga** asoslanadi, tarmoq segmentatsiyasiga emas —
  hozirgi Docker Compose bosqichida ham xavfsiz, kelajakda K8s'ga o'tganda ham
  qo'shimcha o'zgarishsiz ishlaydi.
- Qo'shimcha infratuzilma (service mesh, sertifikat boshqaruvi) talab qilmaydi
  — loyihaning "learning project, kichik qadamlar" tamoyiliga mos.
- Access token allaqachon qisqa muddatli (15 daqiqa, auth-service tech spec'iga
  ko'ra) — ichki chaqiruvlar zanjiri (odatda millisekundlar) uchun bu muddat
  yetarlicha xavfsiz.
- Kelajakda Kubernetes/service mesh (Variant C) qo'shilganda, bu **B'ning
  o'rnini bosmaydi, ustiga qo'shiladi** — C service identity'ni ta'minlaydi,
  B esa user identity'ni. Ikkalasi bir-biriga zid emas.

### Amalga oshirish

- Har bir service (auth-service dan tashqari — u JWT'ni yaratadi) JWT'ni
  tekshirish uchun umumiy `JWT_SECRET` (yoki kelajakda public/private key
  juftligi) ga ega bo'lishi kerak.
- Har bir service o'zining `JwtAuthenticationFilter`'iga ega bo'ladi — bu
  boilerplate kodni kelajakda `finbank-common` shared library'ga chiqarish
  ko'rib chiqiladi (ADR-0001'da eslatilgan, Sprint 3-4 atrofida).
- Servicelar orasidagi chaqiruvda (masalan transaction-service ->
  ledger-service) original `Authorization` header **forward** qilinishi kerak
  — yangi token yaratilmaydi, mavjudi uzatiladi.
- Agar service o'zi Kafka orqali asinxron event yuborsa (masalan
  `TransactionCompleted`) — bu holatda JWT kerak emas, chunki event
  ichki tizim ichida, foydalanuvchi so'rovidan mustaqil ishlaydi. JWT faqat
  **sinxron, so'rov-javob zanjiri** uchun kerak.

### Trade-off

- Har bir service konfiguratsiyasida shared secret bo'lishi kerak — agar bu
  secret oshkor bo'lsa, butun tizim tahdid ostida qoladi (kelajakda public/
  private key juftligiga o'tish bu xavfni kamaytiradi — bu keyingi ADR bo'lishi
  mumkin).
- Har bir service o'zining JWT validation logikasini takrorlaydi (kodning
  bir joyga markazlashmagani) — `finbank-common`ga chiqarilguncha tech debt.

## Natija

Auth Service tomonidan yaratilgan JWT — butun platformaning yagona
autentifikatsiya vositasi. Har bir yangi service yaratilganda, uning tech
spec'ida "JWT validatsiyasi qanday amalga oshiriladi" bandi bo'lishi shart.

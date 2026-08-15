# finbank-auth-service

FinBank platformasidagi barcha foydalanuvchi turlari (CUSTOMER, OPERATOR, ADMIN,
AUDITOR) uchun autentifikatsiya va session boshqaruvi. Bu servis faqat "sen
kimsan, token'ing haqiqiymi" savoliga javob beradi — avtorizatsiya qarorini
boshqa servis'lar JWT ichidagi `role` claim asosida o'zi qabul qiladi.

To'liq biznes qoidalar va arxitektura qarorlari uchun: [`CLAUDE.md`](./CLAUDE.md).
Dastlabki texnik topshiriq va platforma-keng arxitektura qarorlari (ADR'lar) uchun:
[`docs/auth-service-tech-spec.md`](./docs/auth-service-tech-spec.md) va
[`docs/adr/`](./docs/adr/).

## Stack

Java 21, Spring Boot 4.1, Gradle, PostgreSQL, Redis, Kafka.

## Talablar

- JDK 21
- Docker (lokal infra va testlar uchun — testlar Testcontainers orqali real
  Postgres/Kafka/Redis'ni avtomatik ko'taradi)

## Lokal ishga tushirish

Ikki xil usul bor:

### 1. Ilova host mashinada, infra container'da (tez-tez o'zgartirish uchun qulay)

```bash
docker compose up -d postgres redis kafka
./gradlew bootRun
```

Bu `local` profilini ishlatadi (standart, `spring.profiles.default`), va
`localhost`dagi container port'lariga ulanadi.

### 2. Hammasi container'da (build qilingan image'ni sinash uchun)

```bash
docker compose up -d --build
```

Bu ilovaning o'zini ham qurib, `docker` profilida (`SPRING_PROFILES_ACTIVE=docker`)
ishga tushiradi — konteynerlar bir-biriga service nomi orqali ulanadi
(`postgres`, `redis`, `kafka`), `localhost` orqali emas.

Ikkala holatda ham ilova `http://localhost:8080`da ishlaydi.

## Birinchi ADMIN yaratish

Ochiq `/register` faqat CUSTOMER yaratadi. Birinchi ADMIN'ni ikki xil usulda
olish mumkin:

**1. Avtomatik (tavsiya etiladi)** — quyidagi uchta env var to'liq berilsa va
hali birorta ham ADMIN mavjud bo'lmasa, ilova ishga tushganda o'zi yaratadi
(`BootstrapAdminRunner`):

```bash
export BOOTSTRAP_ADMIN_USERNAME=admin
export BOOTSTRAP_ADMIN_EMAIL=admin@finbank.uz
export BOOTSTRAP_ADMIN_PASSWORD='StrongPassword123'
./gradlew bootRun
```

**2. Qo'lda** — oddiy user'ni `/register` orqali yaratib, keyin DB'da rolini
o'zgartirish:

```bash
docker exec -it finbank-auth-service-postgres-1 \
  psql -U finbank -d finbank_auth \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'sizning@emailingiz';"
```

Shundan keyin `/login` orqali ADMIN token oling va `POST /internal/staff` orqali
qolgan xodimlarni (OPERATOR/ADMIN/AUDITOR) yarating.

## Testlarni ishga tushirish

```bash
./gradlew test
```

Barcha testlar (unit + `@WebMvcTest` + `@DataJpaTest` + to'liq integration)
Testcontainers orqali **real** Postgres/Kafka/Redis'ga ulanadi — Docker
ishlab turishi shart. Hech qanday qo'shimcha konfiguratsiya kerak emas.

## Profillar

| Profil | Qachon ishlatiladi | Xususiyati |
|---|---|---|
| `local` | Ilova host mashinada, infra `docker-compose`da (standart) | `localhost` host'lar, dev-only JWT secret (default bor) |
| `docker` | Ilova ham container ichida (`docker compose up --build`) | Service nomlari (`postgres`/`redis`/`kafka`) host sifatida |
| `prod` | Haqiqiy deploy | Hech qanday default yo'q — barcha maxfiy qiymatlar (`JWT_SECRET`, `DB_*`, `REDIS_*`, `KAFKA_*`) majburiy env var orqali beriladi, aks holda ilova ishga tushmaydi |

## Muhim env var'lar (`prod` profili uchun)

| Env var | Tavsif |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL ulanish ma'lumotlari |
| `JWT_SECRET` | HS512 uchun kamida 256-bit tasodifiy qiymat (`openssl rand -hex 64`) |
| `REDIS_HOST`, `REDIS_PASSWORD` | Redis (access-token blacklist uchun ishlatiladi) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker(lar) |
| `BOOTSTRAP_ADMIN_USERNAME/EMAIL/PASSWORD` | Ixtiyoriy — birinchi ADMIN'ni avtomatik yaratish uchun |

## API

`POST /register`, `/login`, `/refresh`, `/logout`, `/logout-all`,
`GET /sessions`, `DELETE /sessions/{id}`, `/password-reset/request`,
`/password-reset/confirm`, `POST /internal/staff` (faqat ADMIN).

### `POST /register` va `Idempotency-Key`

Client so'rovga ixtiyoriy `Idempotency-Key` header'ini qo'shishi mumkin (masalan,
UUID). Shu key bilan qayta yuborilgan so'rov qayta ishlanmaydi — birinchi
urinishning natijasi (muvaffaqiyat yoki duplicate-email xatosi) aynan
qaytariladi. Bu tarmoq uzilishi sabab javob yo'qolgan holatda client'ning
xavfsiz retry qilishi uchun. Key 24 soat davomida Redis'da saqlanadi. Header
berilmasa, endpoint avvalgidek ishlaydi (idempotentlik kafolati yo'q).

## CI

GitHub Actions (`.github/workflows/ci.yml`) har bir push/PR'da `./gradlew build`
(kompilyatsiya + to'liq test suite) ishga tushiradi. Qodana code quality skani
ham alohida workflow sifatida ishlaydi.

## Xavfsizlik bo'yicha eslatmalar

**`PasswordResetRequestedEvent` ichida xom (raw) reset-token bor.** Bu event
`PasswordResetServiceImpl`da parol tiklash so'ralganda Kafka'ga xabar
yuboriladi — email yuborish service'i shu event'ni o'qib, foydalanuvchiga
havola jo'natadi. Token DB'da faqat hash holida saqlanadi (`token_hash`), lekin
Kafka topic'idagi event payload'ida xom holicha ketadi, chunki email yuboruvchi
consumer'ga faqat hash emas, ishlaydigan link kerak.

Bu — token'ni email orqali yuborishning tabiiy natijasi (email ham "plain
text"da boradi), lekin Kafka topic'i email'dan farqli o'laroq ko'pincha
ko'proq consumer/log tizimga ochiq bo'ladi. Shuning uchun:

- `password-reset-requested` topic'iga **faqat** email-yuboruvchi consumer
  group'i uchun ACL/ruxsat berilishi kerak (boshqa hech qanday consumer
  o'qiy olmasligi kerak).
- Broker darajasida topic uchun qisqa retention (masalan, bir necha soat)
  o'rnatish tavsiya etiladi — token 15 daqiqada muddati tugaydi, uzoq
  saqlashning hojati yo'q.
- Kafka'da encryption-at-rest yoqilgan bo'lishi kerak (prod uchun majburiy).

Kod darajasida o'zgartirish qilinmadi — bu operatsion/infratuzilma konfiguratsiyasi
sifatida hal qilinishi kerak bo'lgan masala.

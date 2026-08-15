# finbank-auth-service

FinBank platformasidagi barcha foydalanuvchi turlari (CUSTOMER, OPERATOR, ADMIN,
AUDITOR) uchun autentifikatsiya va session boshqaruvi. Bu servis faqat "sen
kimsan, token'ing haqiqiymi" savoliga javob beradi — avtorizatsiya qarorini
boshqa servis'lar JWT ichidagi `role` claim asosida o'zi qabul qiladi.

To'liq biznes qoidalar va arxitektura qarorlari uchun: [`CLAUDE.md`](./CLAUDE.md).

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

## CI

GitHub Actions (`.github/workflows/ci.yml`) har bir push/PR'da `./gradlew build`
(kompilyatsiya + to'liq test suite) ishga tushiradi. Qodana code quality skani
ham alohida workflow sifatida ishlaydi.

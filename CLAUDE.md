# CLAUDE.md — finbank-auth-service

## Nima uchun

FinBank platformasida barcha foydalanuvchi turlarini autentifikatsiya qilish va
session'larini boshqarish. Bu service faqat "sen kimsan, token'ing haqiqiymi"ni
tasdiqlaydi — avtorizatsiya (kim nimani qila oladi) qarorini boshqa service'lar
JWT ichidagi `role` claim asosida o'zi qabul qiladi.

## Stack

Java 21, Spring Boot 4.1.0, Gradle (Groovy DSL), PostgreSQL, Redis, Kafka.

## Kimlarga xizmat qiladi

- **CUSTOMER** — `/register` orqali o'zi ro'yxatdan o'tadi
- **OPERATOR / ADMIN / AUDITOR** — ochiq register yo'q, faqat ADMIN
  `POST /internal/staff` orqali yaratadi

## Asosiy qarorlar

- **Session limit: 5 device.** 6-chi login kelsa, eng eski (kam ishlatilgan)
  session avtomatik chiqariladi. Foydalanuvchi `GET /sessions` orqali barcha
  faol device'larini ko'radi, `DELETE /sessions/{id}` orqali istaganini o'chiradi.
- **Access token** (JWT, 15 daqiqa) + **Refresh token** (random, DB'da faqat
  hash saqlanadi, 7 kun). Har `/refresh`da eski token o'chadi, yangisi beriladi
  (rotation).
- **Refresh token 2 marta ishlatilsa** (o'g'irlik belgisi) — shu user'ning
  barcha session'lari avtomatik yopiladi.
- **Parol reset qilinganda** — barcha session avtomatik yopiladi.
- **5 marta ketma-ket noto'g'ri parol** — hisob 30 daqiqaga bloklanadi.
- Parol hash: **Argon2id**.

## Code style qoidalari

- Barcha kod **SOLID, DRY, KISS** prinsiplariga mos yozilishi shart.
- Har bir klass bitta aniq mas'uliyatga ega bo'lsin (SRP) — service, repository,
  controller, mapper qatlamlari aralashtirilmasin.
- Interfeys orqali abstraksiya (`AuthService` → `AuthServiceImpl`) — bog'liqlik
  konkret klassga emas, interfeysga qaratilsin (DIP).
- Takrorlanadigan logika (masalan duplicate tekshiruvi, mapper kod) umumiy
  metod/klassga chiqarilsin, nusxa ko'chirilmasin (DRY).
- Yechim talab qilingandan ortiq murakkab bo'lmasin — keraksiz abstraksiya,
  spekulyativ "kelajakda kerak bo'lishi mumkin" kod yozilmasin (KISS, YAGNI).
- Professional Java/Spring patternlariga amal qilinsin: DTO ↔ Entity
  aralashtirilmasin, biznes logika controller'da emas, service qatlamida
  bo'lsin, xatoliklar markazlashgan `@RestControllerAdvice` orqali boshqarilsin.

## Data model

- `users` — email, password_hash, role, status, failed_login_count, locked_until
- `sessions` — refresh_token_hash, device_label, ip_address, status, last_used_at, expires_at
- `password_reset_tokens` — token_hash, status, expires_at (15 daqiqa)

## API

`POST /register`, `/login`, `/refresh`, `/logout`, `/logout-all`,
`GET /sessions`, `DELETE /sessions/{id}`, `/password-reset/request`,
`/password-reset/confirm`, `POST /internal/staff` (faqat ADMIN)

## Kafka event'lar

`UserRegistered`, `LoginSucceeded`, `LoginFailed`, `SuspiciousTokenReuse`,
`PasswordChanged`, `PasswordResetRequested`

## Hozirgi holat

Skeleton tayyor (entity, repository, controller, DTO, security config, Flyway
migration). Biznes logika hali yozilmagan — `AuthServiceImpl` metodlari TODO.

**Navbatdagi qadam:** `register()` — happy path + duplicate email tekshiruvi.

To'liq spec: `docs/auth-service-tech-spec.md`
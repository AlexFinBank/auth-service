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
  session avtomatik chiqariladi.
- **Access token** (JWT, 15 daqiqa) + **Refresh token** (random, DB'da faqat
  hash saqlanadi, 7 kun). Har `/refresh`da rotation (eski o'chadi, yangisi
  beriladi).
- **Refresh token 2 marta ishlatilsa** — shu user'ning barcha session'lari
  avtomatik yopiladi (o'g'irlik belgisi).
- **Parol reset qilinganda** — barcha session avtomatik yopiladi.
- **5 marta ketma-ket noto'g'ri parol** — hisob 30 daqiqaga bloklanadi.
- Parol hash: **Argon2id**. Token hash: **SHA-256** (ikkisi boshqa-boshqa
  sabab bilan — parol past entropiyali, token yuqori entropiyali).

To'liq spec: `docs/auth-service-tech-spec.md`

---

## TEKSHIRISH VAZIFASI

Quyida — kodni tech spec bilan solishtirib, to'liq audit qilish uchun
tartib. Har bir bo'limni alohida ko'rib chiq, topilgan nomuvofiqlik yoki
xatoni **aniq fayl va qator ko'rsatib** ro'yxatla. Taxmin qilma — agar
implementatsiya hali yozilmagan bo'lsa ("TODO" yoki `UnsupportedOperationException`
bo'lsa), buni xato deb hisoblama, shunchaki "hali yozilmagan" deb belgila.

### 1. Struktura tekshiruvi

- [ ] `docs/auth-service-tech-spec.md` mavjudmi va eng so'nggi versiyami?
- [ ] Package struktura spec/konvensiyaga mos: `domain`, `repository`,
  `service`, `controller`, `dto`, `security`, `exception`, `config`
- [ ] Entity'lar (`User`, `Session`, `PasswordResetToken`) tech spec § 3
  data model'iga mos ustunlarga ega
- [ ] DTO'lar `record` sifatida yozilganmi (class emas)?
- [ ] Flyway migration data model bilan mos (enum turlari, index'lar)

### 2. Endpoint'lar — tech spec § 10 bilan solishtirish

Har bir endpoint uchun: mavjudmi, HTTP method/path to'g'rimi, auth talabi
to'g'ri qo'yilganmi (public/authenticated/faqat ADMIN)?

- [ ] `POST /register` — public, faqat CUSTOMER yaratadi
- [ ] `POST /login`
- [ ] `POST /refresh`
- [ ] `POST /logout`
- [ ] `POST /logout-all`
- [ ] `GET /sessions`
- [ ] `DELETE /sessions/{id}`
- [ ] `POST /password-reset/request`
- [ ] `POST /password-reset/confirm`
- [ ] `POST /internal/staff` — FAQAT ADMIN, ochiq emas

**Kritik xavfsizlik tekshiruvi:** STAFF (OPERATOR/ADMIN/AUDITOR) uchun
`/register` orqali ro'yxatdan o'tish IMKONI YO'Qligini alohida tasdiqla —
agar kimdir `role` maydonini request body'da o'zgartirib yubora olsa, bu
jiddiy xavfsizlik teshigi.

### 3. Edge/Failure Case'lar — tech spec § 7

Har biri uchun: kodda shu holat **hisobga olinganmi**, va agar
implementatsiya qilingan bo'lsa, **test bilan qoplanganmi**?

- [ ] #1 — 5 marta noto'g'ri parol → LOCKED, 30 daqiqa
- [ ] #2 — LOCKED user login qilsa → 423, qolgan vaqt ko'rsatiladi
- [ ] #3 — Refresh token 2 marta ishlatilsa → barcha session revoke +
  `SuspiciousTokenReuse` event (BU ENG MUHIM STSENARIYA)
- [ ] #4 — 6 device parallel login (concurrency) → faqat 5 tasi qoladi,
  lost update yo'q
- [ ] #5 — Register, email band → 409
- [ ] #6 — Register, 2 marta bir xil so'rov → idempotent, side-effect yo'q
- [ ] #7 — Reset token muddati o'tgan → 410/400
- [ ] #8 — Reset token allaqachon ishlatilgan → 400
- [ ] #9 — Access token muddati o'tgan → downstream 401
- [ ] #10 — STAFF login — fraud-service uchun kuchliroq monitoring belgisi
- [ ] #11 — Kafka mavjud emas → Auth Service o'zi fail bo'lmasligi kerak

### 4. Concurrency — tech spec § 8 (ENG MUHIM QISM)

- [ ] Session limit (5 device) tekshiruvi **atomik**mi? `SELECT ... FOR
      UPDATE` yoki muqobil lock mexanizmi ishlatilganmi?
- [ ] Buni isbotlovchi **parallel thread bilan yozilgan test** bormi
  (masalan bir vaqtda 6-10 ta login so'rovi yuborib, oxirida faqat
  5 ta ACTIVE session qolishini tekshiradigan test)?
- [ ] Agar bu test yo'q bo'lsa yoki lock noto'g'ri joyga qo'yilgan bo'lsa —
  bu ENG KATTA muammo, alohida ajratib ko'rsat

### 5. Xavfsizlik — tech spec § 9

- [ ] Parol — Argon2id bilan hash qilinganmi? (bcrypt yoki boshqa emas)
- [ ] Refresh token va reset token — DB'da faqat SHA-256 hash, xom qiymat
  hech qayerda saqlanmayaptimi (log'larda ham)?
- [ ] Rate limiting mavjudmi (`/login` uchun IP va email bo'yicha)?
- [ ] Password reset response — email mavjudligini oshkor qilmayaptimi
  (bir xil javob, email bor-yo'qligidan qat'iy nazar)?
- [ ] JWT secret hardcode qilinmaganmi (environment variable orqalimi)?

### 6. Kod sifati va konvensiyalar

- [ ] TODO/`UnsupportedOperationException` qoldirilgan joylarda spec
  bo'limiga aniq havola bormi (masalan "tech spec § 7 #3")?
- [ ] Lombok `@Data` ishlatilmaganmi (mutable equals/hashCode xavfi)?
- [ ] Har bir muhim biznes qaror kodda izohlanganmi?

### 7. Build va konfiguratsiya

- [ ] `build.gradle` Spring Boot 4.1.0 uchun to'g'ri starter nomlariga ega
  (`spring-boot-starter-webmvc`, `spring-boot-starter-kafka`,
  `spring-boot-starter-flyway` — 3.x nomlari emas)
- [ ] `./gradlew build` xatosiz o'tadimi? Agar xato bo'lsa — aniq xato
  xabarini keltir, taxmin qilib "tuzatib qo'yma", faqat top va ro'yxatla
- [ ] `./gradlew test` xatosiz o'tadimi?

## Yakuniy hisobot formati

Tekshiruv oxirida quyidagi tuzilishda hisobot ber:

1. **Yaxshi ishlagan joylar** — spec'ga to'liq mos qismlar
2. **Hali yozilmagan (TODO)** — bu xato emas, shunchaki ro'yxat
3. **Nomuvofiqliklar** — spec bilan kod farq qiladigan joylar (fayl:qator bilan)
4. **Xavfsizlik xavotirlari** — jiddiylik darajasi bilan (past/o'rta/yuqori)
5. **Tavsiya etilgan keyingi qadam** — eng muhim 1-3 ta tuzatish
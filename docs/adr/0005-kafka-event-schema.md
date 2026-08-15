# ADR-0005: Kafka Event Schema Boshqaruvi

**Status:** Accepted
**Sana:** 2026-08-15
**Qamrov:** Barcha FinBank microservice'lari (platform-wide qaror)

## Kontekst

14+ mustaqil deploy bo'ladigan service (multi-repo, ADR-0001) Kafka orqali
event'lar bilan almashadi (masalan `TransactionCompleted`, `UserRegistered`).
Producer o'z tomonidan event shaklini o'zgartirsa (maydon qo'shish/o'chirish/
qayta nomlash), consumer'lar bu haqda **compile vaqtida bilmaydi** — chunki
ular alohida repo. Bu runtime'da, ba'zan jim (silent) xatoga olib kelishi
mumkin — bank uchun xavfli holat.

## Ko'rib chiqilgan variantlar

**A — To'liq Schema Registry (Confluent/Apicurio) + Avro yoki Protobuf.**
Markazlashtirilgan server, avtomatik compatibility tekshiruvi (masalan
BACKWARD), generatsiya qilingan class'lar.

**B — Umumiy "event contracts" kutubxonasi + additive-only qoida.** Alohida
kichik repo (`finbank-event-contracts`), event shakllari (record/POJO)
versiyalangan Gradle dependency sifatida publish qilinadi. Compatibility
qoidasi qo'lda kuzatiladi (faqat qo'shish, hech qachon o'chirish/qayta
nomlash mumkin emas).

## Qaror

**Hozircha B**, keyinroq (Kafka producer/consumer soni ko'payganda,
taxminan roadmap'dagi Kafka bosqichi — Sprint 4 atrofida) **A'ga o'tiladi**.

### Nega hozir B

- Hozirgi bosqichda faqat 1 ta service (`auth-service`) tayyor, Kafka
  producer/consumer kod hali yozilmagan. To'liq Schema Registry qo'shish
  bu bosqichda foydasi ko'rinmaydigan ortiqcha infratuzilma bo'lardi.
- Shared library — kod darajasida hech bo'lmaganda compile-time xavfsizlik
  beradi (agar consumer eski class versiyasini ishlatsa, bog'lanish xatosi
  kamida build vaqtida ko'rinadi to'liq runtime muammosidan ko'ra).
- ADR-0002 va ADR-0004'dagi mulohaza bilan bir xil tamoyil: hozir kerak
  bo'lmagan infratuzilmani oldindan qo'shmaslik.

### Nega keyinroq A

- Loyiha hujjatida "Schema evolution va event versioning" aniq o'rganish
  maqsadi sifatida yozilgan (11-bo'lim) — bu maqsad **rad etilmaydi, faqat
  kechiktiriladi**, chunki Schema Registry'ning haqiqiy qiymati faqat
  ko'plab producer/consumer bo'lgandagina (real compatibility konflikti
  yuzaga kelganda) sezilarli bo'ladi.
- Producer/consumer soni ko'payganda, qo'lda kuzatiladigan additive-only
  qoida yetarli bo'lmay qoladi — inson xatosi ehtimoli oshadi.

### Amalga oshirish (hozirgi bosqich)

- Yangi repo: `finbank-event-contracts` — barcha Kafka event'larining
  shakli (Java record'lar, JSON serialization uchun mos) shu yerda
  yoziladi va versiyalanadi.
- Har bir producer va consumer service shu repo'ni Gradle dependency
  sifatida qo'shadi (`implementation("uz.finbank:event-contracts:x.y.z")`).
- **Additive-only qoida**: mavjud event'ga faqat yangi (nullable/optional)
  maydon qo'shish mumkin. Mavjud maydonni o'chirish, qayta nomlash, yoki
  turini o'zgartirish — **yangi event versiyasi** sifatida qaraladi
  (masalan `TransactionCompletedV2`).
- Har bir event class'ida versiya va "qachon qo'shilgani" haqida izoh
  bo'lishi shart.

### Trade-off

- Additive-only qoidaning buzilishini **hech narsa avtomatik ushlamaydi** —
  bu to'liq code review intizomiga bog'liq.
- `finbank-event-contracts`ga o'zgartirish kiritilganda, uni ishlatuvchi
  barcha service'larni versiyani yangilashga eslatib turish kerak (bu ham
  qo'lda kuzatiladigan jarayon, hozircha).

## Natija

- Yangi repo `finbank-event-contracts` yaratiladi, birinchi event'lar
  (`UserRegistered`, `LoginSucceeded`, `LoginFailed`, `SuspiciousTokenReuse`,
  `PasswordChanged`, `PasswordResetRequested` — auth-service tech spec'idan)
  shu yerga ko'chiriladi.
- Kelajakda Confluent/Apicurio Schema Registry'ga o'tish alohida ADR sifatida
  hujjatlashtiriladi (bu ADR'ga havola bilan, "nega avval B tanlangani va
  nega A'ga o'tilgani" tushunarli bo'lishi uchun).

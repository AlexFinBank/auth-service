# ADR-0001: Repo Strategiyasi va Build Tool

**Status:** Accepted
**Sana:** 2026-08-14
**Sprint:** 1

## Kontekst

FinBank — 14+ microservice'dan iborat digital banking platform (learning project).
Loyihani boshlashdan oldin ikki asosiy infrastruktura qarori qabul qilinishi kerak:

1. Repo strategiyasi: monorepo yoki multi-repo
2. Build tool: Maven yoki Gradle

## Qaror 1: Multi-repo

Har bir microservice o'zining alohida Git repositoriyasida yashaydi
(`finbank-auth-service`, `finbank-account-service`, `finbank-transaction-service`, va h.k.).

### Nega multi-repo tanlandi

- Real production banking tizimlarida standart amaliyot — har bir service mustaqil
  deploy qilinadi, mustaqil versiyalanadi, mustaqil CI/CD pipeline'ga ega bo'ladi.
- Service boundary'larni "majburiy" saqlaydi — bir repo ichida bo'lganda ba'zan
  service'lar orasida yashirin coupling paydo bo'ladi (masalan bir-birining internal
  class'ini import qilish). Alohida repo bu imkonsiz qiladi.
- Independent deployment tajribasi — har bir service o'z release cycle'iga ega
  bo'lishi qanday his qilinishini o'rganish uchun foydali.

### Trade-off (bilib turib qabul qilingan)

- Qo'shimcha overhead: har bir repo uchun alohida CI config, README, versioning.
- Cross-service o'zgarish (masalan shared DTO yoki event schema) bir necha repo'da
  bir vaqtda o'zgartirilishi kerak bo'ladi — bu real production'da ham muammo,
  shu sababli buni ham "amaliyot" deb qaraymiz.
- Local development uchun barcha repo'larni bir joyga klonlab, orchestration qilish
  kerak bo'ladi (Docker Compose orqali, alohida `finbank-infra` repo'da).

### Amalga oshirish

- Har bir service repo nomi: `finbank-<service-name>` (masalan `finbank-auth-service`)
- Umumiy infra (Docker Compose, Prometheus/Grafana config) uchun alohida repo:
  `finbank-infra`
- Shared kod kerak bo'lsa (masalan umumiy error response format, event contract'lar)
  — Sprint 3-4 atrofida `finbank-common` yoki alohida published library sifatida
  qarab chiqiladi. Hozircha har bir service o'z-o'zidan yetarli bo'lishi kerak
  (Sprint 1 checklist: "system har bosqichda ishlaydigan holatda qolishi kerak").

## Qaror 2: Gradle (Groovy DSL)

Build tool sifatida Gradle tanlandi (Maven o'rniga), **Groovy DSL** bilan
(`build.gradle`, Kotlin DSL `.kts` emas).

> **Tuzatish tarixi:** Bu ADR boshida Kotlin DSL sifatida yozilgan edi, lekin
> implementatsiya bosqichida (Spring Initializr orqali generatsiya qilinganda)
> haqiqatda Groovy DSL ishlatildi. ADR real amaliyotga mos bo'lishi kerak degan
> qoidaga ko'ra, shu yerda tuzatildi — qaror hujjati va kod doim bir xil haqiqatni
> aks ettirishi kerak.

### Nega Gradle (umuman)

- Multi-module/multi-repo Spring Boot loyihalarida Maven'ga nisbatan tezroq build
  (incremental build, build cache).
- Docker/Testcontainers integratsiyasi uchun plugin ekotizimi boy.

### Nega Groovy DSL (Kotlin DSL emas)

- Spring Initializr'ning standart (default) generatsiyasi Groovy DSL — bu loyiha
  Spring Initializr orqali boshlangani uchun qo'shimcha o'zgartirishsiz shu bilan
  davom etildi.
- Internetdagi Spring Boot + Gradle misollarining katta qismi hali ham Groovy
  DSL'da yozilgan — o'rganish jarayonida (bu loyiha "learning project" ekanini
  hisobga olsak) mos misol topish osonroq.
- Groovy — dinamik, qisqa sintaksis; Kotlin DSL'ning IDE type-safety afzalligi
  bu loyiha o'lchamida (hozircha bitta kichik service) muhim emas.

### Trade-off

- Maven'ga qaraganda community/StackOverflow resurslari kamroq (lekin Spring Boot
  bilan ishlashda bu farq katta emas).
- Kotlin DSL'ga nisbatan IDE'da avtomatik to'ldirish (autocomplete) va compile-time
  xatolarni aniqlash birmuncha kuchsizroq (Groovy dinamik tipli til).
- Groovy sintaksisini o'rganish kerak (agar avval faqat XML-based Maven bilan
  ishlangan bo'lsa) — buning o'rniga Kotlin DSL ham xuddi shu muammoga ega bo'lardi.

## Natija

- Har bir yangi service uchun repo yaratilganda: `finbank-<name>-service` nomlanishi,
  Gradle (**Groovy DSL**, `build.gradle`) bilan Spring Boot 4.x + Java 21 skeleton.
- Bu qaror keyingi ADR'lar bilan bog'liq: ADR-0002 (service-to-service autentifikatsiya)
  va ADR-0005 (shared event-contracts strategiyasi, `finbank-event-contracts`).
  > **Tuzatish tarixi:** bu bandda ilgari "ADR-0002 (database-per-service)" deb yozilgan
  > edi, lekin repodagi haqiqiy ADR-0002 mavzusi service-to-service autentifikatsiya —
  > database-per-service alohida ADR sifatida hali yozilmagan. Havola shu sababli
  > mavjud ADR'larga to'g'irlandi (ADR-0005'dagi eslatma bilan bir xil qoidaga ko'ra:
  > qaror hujjati kod/boshqa hujjatlar bilan bir xil haqiqatni aks ettirishi kerak).

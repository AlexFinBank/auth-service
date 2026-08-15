# ADR-0008: Concurrency Model — Virtual Threads (Reactive emas)

**Status:** Accepted
**Sana:** 2026-08-15
**Qamrov:** Barcha FinBank microservice'lari (platform-wide qaror, api-gateway bundan mustasno)

## Kontekst

Yuqori concurrency (maqsad: 20,000+ API so'rov/soniya) uchun ikkita asosiy
yondashuv bor: **reactive** (Spring WebFlux/Project Reactor, non-blocking
event-loop) yoki **an'anaviy blocking** (Spring MVC, thread-per-request).
Tarixan reactive — ko'p parallel so'rovni kam sonli thread bilan boshqarish
uchun yaratilgan, chunki OS thread'lar qimmat (xotira, context-switching).

Java 21'dan beri uchinchi variant mavjud: **Virtual Threads** (Project Loom)
— JVM darajasida juda arzon thread'lar, bu reactive'ning asosiy muammosini
(thread exhaustion) **oddiy, blocking kod bilan** hal qiladi.

## Ko'rib chiqilgan variantlar

**A — Spring WebFlux (to'liq reactive).** Non-blocking I/O, `Mono`/`Flux`
asosidagi funksional dasturlash uslubi.

**B — Spring MVC + Virtual Threads.** An'anaviy, imperativ (ketma-ket) kod,
lekin `spring.threads.virtual.enabled=true` bilan.

**C — Spring MVC, oddiy platform thread'lar (hech narsa o'zgarmaydi).**

## Qaror

**Variant B** — barcha asosiy biznes service'lar (auth, account, customer,
transaction, ledger, fraud va h.k.) uchun Spring MVC + Virtual Threads.

**Istisno: `api-gateway`** — bu qaror doirasidan tashqarida, alohida ko'rib
chiqiladi (pastga qarang).

### Nega A (to'liq reactive) emas

- Loyihaning asosiy persistence texnologiyasi — **JPA/Hibernate**, bu
  tabiatan **blocking** (JDBC sinxron ishlaydi). Reactive'ga o'tish uchun:
  - JPA'ni R2DBC'ga almashtirish kerak bo'lardi — bu ADR-0006'dagi kabi
    concurrency mexanizmlar (`@Version` optimistic locking, `SELECT ...
    FOR UPDATE` pessimistic locking) R2DBC ekotizimida unchalik pishiq/yetuk
    emas.
  - Yoki JPA'ni saqlab, uni alohida "elastic scheduler" thread pool'iga
    chiqarish kerak bo'lardi — bu holda reactive'ning asosiy afzalligi
    (kam thread bilan yuqori concurrency) **yo'qqa chiqadi**, faqat
    murakkablik qo'shiladi (ikkala dunyoning eng yomon tomoni).
- Reactive dasturlash — butunlay boshqa paradigma (funksional, callback
  zanjirlari), o'rganish va debug qilish sezilarli qiyinroq. Loyihaning
  "concept → experiment → apply → test" tsikliga to'sqinlik qilishi mumkin.

### Nega B (Virtual Threads)

- Virtual Threads — reactive'ning asosiy muammosini (thread-per-request
  model'ning ko'p parallel so'rovda cheklanishi) **oddiy, imperativ kod
  bilan** hal qiladi. JVM virtual thread'larni juda arzon yaratadi;
  blocking chaqiruv (DB, tarmoq) bo'lganda, virtual thread "parkovka"
  qilinadi, real OS thread bo'shab boshqa so'rovga xizmat qiladi.
- JPA/Hibernate bilan **to'liq mos** — hech qanday kod o'zgarishi kerak
  emas, faqat konfiguratsiya (`spring.threads.virtual.enabled=true`).
- Debug qilish, stack trace o'qish — an'anaviy blocking kod kabi oddiy
  qoladi (reactive'dagi "qayerda uzilgan" degan muammo yo'q).
- Concurrency bo'yicha zamonaviy, real production'da (2023-yildan beri)
  tobora ko'proq qo'llanilayotgan yondashuvni o'rganish imkoni — bu ham
  loyihaning "production-level muammolarni o'rganish" maqsadiga mos.

### Istisno — `api-gateway`

`api-gateway` — sof proxy/routing qatlami, JPA yoki boshqa blocking
resurslarga bog'liq emas. Bu — **ideal reactive use-case** (aynan shuning
uchun Spring'ning o'zi Spring Cloud Gateway'ni WebFlux ustiga qurgan).
`api-gateway` uchun WebFlux ishlatish alohida ko'rib chiqiladi, chunki bu
yerda reactive'ning kamchiliklari (JPA bilan mos kelmaslik) mavjud emas.

`notification-service` (agar kelajakda WebSocket orqali real-time push
qo'shilsa) — bu ham reactive'dan foyda ko'rishi mumkin bo'lgan kandidat,
lekin bu qaror **hozircha ochiq qoldiriladi**, real yuk testi natijalariga
qarab keyinroq hal qilinadi.

### Amalga oshirish

- Har bir yangi service'ning `application.yml`/`.properties`'ida:
  ```yaml
  spring:
    threads:
      virtual:
        enabled: true
  ```
- Mavjud service'lar (`auth-service`, `account-service`, `customer-service`)
  ham shu konfiguratsiyani qo'shishi kerak.
- **Diqqat:** virtual thread'lar `synchronized` blok ichida "pinning"
  muammosiga duch kelishi mumkin (virtual thread `synchronized` ichida
  bloklansa, u real OS thread'ga "yopishib qoladi", virtual thread'ning
  asosiy afzalligini yo'qotadi). Kod review'da `synchronized` ishlatilgan
  joylarga alohida e'tibor berish kerak — `ReentrantLock` odatda xavfsizroq
  muqobil.

### Trade-off

- Virtual Threads — nisbatan yangi texnologiya (Java 21, 2023), ba'zi
  kutubxonalar/monitoring vositalari hali to'liq moslashmagan bo'lishi
  mumkin (masalan ba'zi profiler'lar virtual thread'larni to'liq
  ko'rsatmasligi mumkin).
- `synchronized` bilan bog'liq "pinning" muammosi — bu yangi turdagi xato,
  jamoada (yoki bu holda — loyiha egasida) alohida o'rganish talab qiladi.

## Natija

Barcha yangi va mavjud biznes service'lar Spring MVC + Virtual Threads bilan
davom etadi. `api-gateway` qurilganda, uning uchun WebFlux'dan foydalanish
alohida ADR sifatida qarab chiqiladi.

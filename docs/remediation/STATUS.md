# Стан виконання

План підготовлено 2026-09-21. Реалізацію й новий запуск тестів у межах підготовки плану не виконували.

| ID | Статус | Доказ / примітка |
| --- | --- | --- |
| 00 | FAIL | Свіжий `mvn clean verify`: 237 тестів, 0 failures, 1 error; помилка в `ReadableLoggingTest` |
| 01 | PASS | ERROR-лог 500 містить метод, URI без query, MDC correlationId і stack trace; відповідь лишилася загальною |
| 02 | PASS | Немає implicit dev/fallback dev; обов'язкові DB/інтеграційні параметри та безпечний базовий SQL-режим перевірені |
| 03 | PASS | JWT-запит lookup-ить користувача за userId; роль/email беруться з БД, DB failure не маскується як 401 |
| 04 | PASS | Email канонізується до валідації та захищений у PostgreSQL check + unique constraint |
| 05 | PASS | Stripe/provider errors розділено на 502/503, конфлікти стану — 409 |
| 06 | PASS | GET success/cancel стали read-only; webhook лишився шляхом фіналізації |
| 07 | PASS | Прибрано недосяжний payment helper, зайві availability helpers і прямі testng/liquibase залежності |
| 08 | PASS | FK bookings/accommodation повертає зрозумілий 409 без маскування інших integrity-помилок |
| 09 | SKIPPED | Наявні конфіги описують лише same-origin Swagger і прямий локальний доступ; proxy/frontend відсутні |
| 10 | FAIL | Фінальний verify: 268 тестів, 0 failures, 1 error — відомий ReadableLoggingTest |

## Вихідні дані

Попередні локальні surefire-звіти сумарно містили 237 виконань, 6 failures і 2 errors. Частина стосується SQL до наявних змін із CAST; це не висновок про поточний стан. Не використовувати ці числа як актуальний baseline або свідчення проходження тестів.

## Короткі звіти виконавців

Додавати по одному стислому запису на завершений крок або передачу.

00 / GPT-5.6 Luna / 2026-09-21:
Статус: FAIL
Зміна: Прикладний код і тести не змінювалися. Виконано свіжий повний baseline; збірка зупинилася на єдиній помилці тесту.
Файли: лише `docs/remediation/STATUS.md`.
Перевірка: `mvn clean verify`, exit code 1; tests=237, failures=0, errors=1, skipped=0. JDK Maven: 21.0.9; Maven: 3.9.11; Docker/Testcontainers доступні після дозволеної поза-sandbox перевірки.
Невиконані критерії / обмеження: PASS неможливий. `ReadableLoggingTest.textContainsCorrelationAndTransitionFields` падає на `ObjectMapper.readTree(line)` (line 45): текстовий лог не є JSON; відповідний звіт — `target/surefire-reports/com.bookingapp.infrastructure.observability.ReadableLoggingTest.txt`. Старі SQL-падіння не підтверджені цим прогоном.
Наступний виконавець: Terra / medium: відтворити `mvn -Dtest=ReadableLoggingTest test`, визначити контракт для text-профілю логування та виправити лише узгодження тесту/формату; після цього повторити повний `mvn clean verify`.

01 / GPT-5.6 Luna / 2026-09-21:
Статус: PASS
Зміна: `handleUnhandledException` тепер пише ERROR structured-лог із HTTP-методом, URI без query та cause для stack trace; correlationId береться з наявного MDC через конфігурацію логування, без другого механізму кореляції. Додано MockMvc-сценарій 500 із перевіркою загальної відповіді та відсутності секретів header/query.
Файли: `src/main/java/com/bookingapp/exception/GlobalExceptionHandler.java`, `src/test/java/com/bookingapp/web/booking/BookingControllerTest.java`, `docs/remediation/STATUS.md`.
Перевірка: `mvn '-Dtest=BookingControllerTest,StructuredLoggingTest' test`, exit code 0; tests=9, failures=0, errors=0, skipped=0. Checkstyle: 0 violations.
Невиконані критерії / обмеження: Команда з `ReadableLoggingTest` також запускалася, але цей відомий baseline-тест упав із попередньою помилкою JSON-парсингу text-профілю; його незакомічені правки не змінювалися. Платіжну логіку, `SafeFailure` і захист Telegram не змінював.
Наступний виконавець: немає; не запускати картку 02 у межах цього кроку.

02 / GPT-5.6 Luna / 2026-09-21:
Статус: PASS
Зміна: Прибрано неявний профіль `dev`, fallback `booking_password` і працездатні Stripe/Telegram defaults; звичайний запуск має `show-sql=false` та WARN для SQL/bind, а dev-діагностика лишилася явною. Compose тепер вимагає `DB_PASSWORD` і `SPRING_PROFILES_ACTIVE`; `.env.sample` містить явні шаблони з поясненням, а відомі Stripe/Telegram placeholders відхиляються startup-валідацією.
Файли: `application.yml`, `StripeProperties.java`, `TelegramProperties.java`, `docker-compose.yml`, `.env.sample`, `README.md`, `README-UA.md`, `RuntimeConfigurationTest.java`.
Перевірка: `mvn '-Dtest=RuntimeConfigurationTest,JwtTokenServiceTest' test`, exit code 0; tests=15, failures=0, errors=0, skipped=0. `mvn '-Dtest=BookingAppContextTest' test`, exit code 0; tests=1, failures=0, errors=0, skipped=0. `docker compose config --quiet`, exit code 0. Checkstyle: 0 violations.
Невиконані критерії / обмеження: Повний suite не запускався в межах картки 02; реальний `.env` не читався й секрети не виводилися. Чинну JWT/webhook-валідацію та платіжну логіку не змінював.
Наступний виконавець: немає; не запускати картку 03 у межах цього кроку.

03 / GPT-5.6 Luna / 2026-09-21:
Статус: PASS
Зміна: JWT filter після перевірки підпису/строку читає лише `userId`, робить один lookup у `UserRepositoryImpl` і будує principal/authorities з поточного запису БД. Відсутній користувач і bad/expired JWT дають 401; помилка lookup дає оброблений generic 500 з ERROR stack trace; downstream exceptions не перехоплюються filter-ом. README описано актуальні права, lookup на запит і обмеження TTL.
Файли: `JwtAuthenticationFilter.java`, `JwtTokenService.java`, `JwtAuthenticationFilterTest.java`, `JwtTokenServiceTest.java`, `UserControllerIntegrationTest.java`, `README.md`, `README-UA.md`.
Перевірка: `mvn '-Dtest=JwtTokenServiceTest,JwtAuthenticationFilterTest' test`, exit code 0; tests=13, failures=0, errors=0, skipped=0. `mvn '-Dtest=AuthControllerIntegrationTest,UserControllerIntegrationTest,AccommodationControllerIntegrationTest,HealthControllerIntegrationTest' test`, exit code 0; tests=28, failures=0, errors=0, skipped=0. Checkstyle: 0 violations.
Невиконані критерії / обмеження: Повний suite не запускався; відомий baseline `ReadableLoggingTest` із картки 00 не змінювався. Не додано refresh token, blacklist, tokenVersion чи кеш; пароль у principal не зберігається.
Наступний виконавець: немає; не запускати картку 04 у межах цього кроку.

04 / GPT-5.6 / 2026-09-21:
Статус: PASS
Зміна: Додано єдиний `trim().toLowerCase(Locale.ROOT)` нормалізатор email і застосовано його в DTO до Bean Validation, у register/login та PUT/PATCH. `PATCH` з `null` лишає старе значення, а blank відхиляється. Міграція 016 до змін перевіряє канонічні конфлікти й HALT-ить із поясненням, нормалізує наявні адреси та додає `ck_users_email_canonical`; чинна унікальність email завершує гарантію для конкурентних операцій. Відповідь race-конфлікту мапиться в `Email is already in use` лише для ідентифікованого email unique constraint. README документує незворотну втрату початкового регістру/пробілів.
Файли: `EmailNormalizationUtils.java`, auth/user DTO і сервіси, `GlobalExceptionHandler.java`, `016-normalize-user-emails.yaml`, master changelog, README/README-UA, auth/user/Liquibase тести, `STATUS.md`.
Перевірка: `mvn '-Dtest=AuthServiceTest,UserServiceTest,AuthControllerIntegrationTest,UserControllerIntegrationTest,LiquibaseMigrationIntegrationTest' test`, exit code 0; tests=35, failures=0, errors=0, skipped=0. Після уточнення обробника race: `mvn '-Dtest=AuthControllerIntegrationTest,LiquibaseMigrationIntegrationTest' test`, exit code 0; tests=11, failures=0, errors=0, skipped=0. Checkstyle: 0 violations. Testcontainers підтвердив чисте застосування migration 016, raw SQL не обходить canonical check, а два синхронні register-запити створюють рівно один account.
Невиконані критерії / обмеження: Повний suite не запускався; відомий baseline `ReadableLoggingTest` із картки 00 не змінювався. Міграція навмисно не має data rollback: знятий check не відновлює регістр або пробіли.
Наступний виконавець: немає; не запускати картку 05 у межах цього кроку.

05 / GPT-5.6 / 2026-09-21:
Статус: PASS
Зміна: `PaymentStateException` і `InvalidBookingStateException` стали конфліктами стану (409). Stripe SDK 28.4.0 класифікується у відокремлені безпечні provider-помилки: connection/rate limit/5xx дають 503, інші відповіді сформованого сервером запиту — 502. Лог містить лише категорію, provider status/request id; raw Stripe message і cause не повертаються. Webhook локально повертає 503 тільки для тимчасової provider-недоступності або гонки з локальним commit, а конфлікт верифікації не маскується успішною відповіддю.
Файли: `exception/PaymentProvider*.java`, `PaymentStateException.java`, `InvalidBookingStateException.java`, `GlobalExceptionHandler.java`, `StripePaymentProvider.java`, `StripeWebhookController.java`, Stripe/payment/booking тести, `STATUS.md`.
Перевірка: `mvn '-Dtest=StripePaymentProviderTest,PaymentServiceTest,BookingServiceTest,PaymentControllerIntegrationTest,PaymentLifecycleIntegrationTest' test`, exit code 0; tests=77, failures=0, errors=0, skipped=0. Checkstyle: 0 violations. Тести покривають create/retrieve/expire connection failure, 502 gateway failure, 409 state conflict, 400 invalid webhook signature та 503→200 retry webhook без дублювання outbox event.
Невиконані критерії / обмеження: Повний suite не запускався; відомий baseline `ReadableLoggingTest` із картки 00 не змінювався. Stripe/Telegram виклики не виконувалися проти реальних сервісів.
Наступний виконавець: немає; не запускати картку 06 у межах цього кроку.

06 / GPT-5.6 / 2026-09-21:
Статус: PASS
Зміна: `GET /payments/success` тепер повертає нейтральний landing без читання Stripe, мутації БД/outbox чи даних платежу; `session_id` ігнорується. `GET /payments/cancel` лишився owner/admin local-read та не викликає Stripe/recovery/expire/save; для pending радить `POST /payments`. OpenAPI/README оновлено; webhook і POST не змінено.
Файли: `PaymentController.java`, `PaymentService.java`, payment controller/service/lifecycle тести, `README.md`, `README-UA.md`, `STATUS.md`.
Перевірка: `mvn '-Dtest=PaymentControllerTest,PaymentControllerIntegrationTest,PaymentLifecycleIntegrationTest,PaymentServiceTest' test`, exit code 0; tests=57, failures=0, errors=0, skipped=0. Checkstyle: 0 violations. Testcontainers перевірив повторні GET success/cancel без Stripe/outbox/DB mutation та webhook finalization/dedup.
Невиконані критерії / обмеження: Повний suite не запускався; baseline `ReadableLoggingTest` картки 00 не змінювався. Реальні Stripe/Telegram не викликалися.
Наступний виконавець: немає; не запускати картку 07 у межах цього кроку.

07 / GPT-5.6 / 2026-09-21:
Статус: PASS
Зміна: Видалено недосяжний приватний `completePayment`, невикликані production-методи `decreaseAvailability`/`increaseAvailability` та їхній helper; алгоритм бронювання й модель availability не змінювалися. Із `pom.xml` прибрано прямі залежності `testng` і `liquibase-core`; Liquibase лишився через `spring-boot-starter-liquibase`. `attachOrRecoverSession` і `confirmPayment` збережено, бо вони викликаються checkout-закриттям.
Файли: `pom.xml`, `src/main/java/com/bookingapp/service/PaymentService.java`, `src/main/java/com/bookingapp/service/AccommodationService.java`, `STATUS.md`.
Перевірка: `mvn '-Dtest=PaymentServiceTest,AccommodationServiceTest,BookingAppContextTest,LiquibaseMigrationIntegrationTest' test`, exit code 0; tests=26, failures=0, errors=0, skipped=0. Checkstyle: 0 violations. Maven використовує Java 21.0.9; context і Liquibase migration тести успішні.
Невиконані критерії / обмеження: Повний suite не запускався; baseline `ReadableLoggingTest` картки 00 не змінювався. Наступні картки не запускалися.
Наступний виконавець: немає; не запускати картку 08 у межах цього кроку.

08 / GPT-5.6 / 2026-09-21:
Статус: PASS
Зміна: Обробник `DataIntegrityViolationException` розпізнає лише точний constraint `fk_bookings_accommodation` і повертає 409 з повідомленням `Accommodation cannot be deleted because it has bookings`; інші integrity-помилки лишаються загальними. Додано інтеграційні перевірки для PENDING і CANCELED booking, без cascade delete чи зміни схеми.
Файли: `GlobalExceptionHandler.java`, `AccommodationControllerIntegrationTest.java`, `AccommodationControllerTest.java`, `STATUS.md`.
Перевірка: `mvn '-Dtest=AccommodationControllerIntegrationTest,AccommodationControllerTest' test`, exit code 0; tests=18, failures=0, errors=0, skipped=0. Checkstyle: 0 violations. Testcontainers підтвердив збереження accommodation і booking після FK-конфлікту.
Невиконані критерії / обмеження: Повний suite не запускався; baseline `ReadableLoggingTest` картки 00 не змінювався. Наступні картки не запускалися.
Наступний виконавець: немає; не запускати картку 09 у межах цього кроку.

09 / GPT-5.6 / 2026-09-21:
Статус: SKIPPED
Зміна: Змін застосунку не виконувалося. `docker-compose.yml` публікує backend напряму на `localhost:8080`, README описує лише same-origin Swagger UI, а Dockerfile/Compose/README не містять reverse proxy, окремого frontend або deployment topology з trusted proxy. За умовою картки CORS і forwarded-header довіра не конфігуруються навмання.
Файли: `STATUS.md`.
Перевірка: read-only перевірка `rg` у `README.md`, `Dockerfile`, `docker-compose.yml`, `SecurityConfiguration.java`, `application.yml`, `AuthController.java`, `AuthRateLimiter.java`; окремі тести не запускалися, бо змін коду немає.
Невиконані критерії / обмеження: Для розгортання за reverse proxy або окремого frontend потрібні allowlist origins, межа довіри proxy та edge-конфіг, після чого картку слід відкрити повторно. Вбудований rate limit лишається per-instance, як документовано в README.
Наступний виконавець: якщо з'явиться окремий frontend/proxy — надати його origins, адресу/мережеву межу proxy та факт прямої доступності backend; не запускати картку 10 у межах цього кроку.

10 / GPT-5.6 / 2026-09-21:
Статус: FAIL
Зміна: Фінальний прогін виявив, що попередня FK-перевірка delete працювала на Hibernate test schema без Liquibase constraint. Додано flush після physical delete і окремий MockMvc integration test на Liquibase schema; виправлено handler fixture, що помилково імітував email constraint, та застаріле очікування 400 замість контрактного 409 для повторного cancel booking. README.md і актуальний README-UA.md перевірені: профілі, required env, JWT lookup, email, payment landing/webhook і outbox delivery відповідають чинним контрактам.
Файли: `AccommodationRepositoryImpl.java`, accommodation controller integration/unit тести, `BookingControllerIntegrationTest.java`, `AccommodationDeleteConstraintIntegrationTest.java`, `STATUS.md`.
Перевірка: після вузького виправлення `mvn '-Dtest=AccommodationControllerIntegrationTest,AccommodationDeleteConstraintIntegrationTest,AccommodationControllerTest,BookingControllerIntegrationTest' test`, exit code 0; tests=39, failures=0, errors=0, skipped=0. Остаточний `mvn clean verify`, exit code 1; tests=268, failures=0, errors=1, skipped=0. Усі обов'язкові integration класи присутні й пройшли. `mvn jacoco:report` із свіжого exec: LINE 1905/2290 (83.19%), BRANCH 362/542 (66.79%). Checkstyle: 0 violations.
Невиконані критерії / обмеження: verify не зелений через `ReadableLoggingTest.textContainsCorrelationAndTransitionFields` — `JsonParseException` у line 45, бо тест передає text log line до `ObjectMapper.readTree`. Через падіння test phase Maven не дійшов власної JaCoCo check/package phase; coverage report згенеровано окремо зі свіжих execution data. 09 обґрунтовано SKIPPED; production-ready не заявляється.
Наступний виконавець: Terra / medium — вирішити контракт `ReadableLoggingTest`: або тест має перевіряти text layout без JSON-парсингу, або test profile має явно обрати JSON layout; після вузького виправлення повторити `mvn clean verify`. Не змінювати CORS/proxy без топології.

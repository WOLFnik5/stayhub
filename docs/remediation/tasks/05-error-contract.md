# 05 — HTTP-контракт помилок платежів і станів

Модель: GPT-5.6 Terra / medium. Передумова: 01.

Файли: exception/*, StripePaymentProvider.java, StripeWebhookController.java, усі throw sites PaymentStateException/InvalidBookingStateException та відповідні тести.

Зараз StripeException втрачає тип і стає PaymentStateException → 400. Не переводь усі StripeException в однаковий код без класифікації.

Обраний контракт:

| Ситуація | HTTP |
| --- | --- |
| Невалідний користувацький ввід | 400 |
| Конфлікт поточного стану бронювання/платежу | 409 |
| Недоступність/timeout/rate limit провайдера | 503 |
| Інша помилка взаємодії/відповіді Stripe для сформованого сервером запиту | 502 |
| Неочікуваний локальний дефект | 500 |

Введи відокремлений тип/типи провайдерної помилки й окремі handler-и. Перевір актуальні класи StripeException у підключеній версії SDK перед класифікацією. Збережи стабільний ApiErrorResponse, не повертай клієнту raw Stripe message або секрети. Для діагностики збережи безпечні категорію, status/request id, якщо доступні; не відновлюй небезпечні cause лише заради stack trace.

Переглянь кожне місце PaymentStateException: помилка суми/metadata не повинна загубитися серед retryable мережевих збоїв. Поточний StripeWebhookController має локальний catch DomainException → 503. Збережи retry при гонці вебхука з локальним commit і недоступності провайдера. Не підміняй невдалу обробку webhook відповіддю 200. Invalid signature лишається 400.

Критерії: мережевий збій на створенні/читанні/закритті checkout більше не 400; конфлікт стану — 409; звичайна validation — 400; підписаний webhook із тимчасовою помилкою — non-2xx, повтор після відновлення успішний без подвійного outbox event.

Тести: StripePaymentProviderTest, PaymentServiceTest, BookingServiceTest, PaymentControllerIntegrationTest, PaymentLifecycleIntegrationTest, нові handler-тести. Не змінювати транзакції або повтори Stripe в цій картці.

# 06 — GET-запити без зміни платежів

Модель: GPT-5.6 Terra / medium. Передумова: 05.

Файли: PaymentController.java, PaymentService.java, PaymentWebMapper/DTO, SecurityConfiguration.java, StripeWebhookController.java (перевірка), платіжні тести й README.

Обраний контракт, щоб не витрачати час на вибір дизайну:

- Публічний GET /payments/success лишається landing endpoint, як /payments/cancel/return: повертає нейтральне повідомлення, що checkout завершено і актуальний статус доступний після входу. Не підтверджує PAID, не розкриває payment/booking дані, не читає Stripe і не змінює БД/outbox. URL з query session_id залишається допустимим, але не надає доступу до деталей. Це навмисна зміна JSON-контракту — описати в README/OpenAPI.
- GET /payments/cancel із перевіркою власника тільки читає локальний стан. Для PENDING не обіцяє, що session досі активна у Stripe; recovery/reconciliation з цього GET прибрати. Відповідь може пояснювати, що повторний checkout потребує POST /payments.
- Підписаний webhook виконує фіналізацію незалежно від browser redirect. Чинний POST /payments може зберегти перевірку/відновлення наявної спроби. Не забороняти reconciliation у всіх шляхах — мета цієї картки лише безпечна семантика GET.

Критерії: повторні GET success/cancel не змінюють payment і outbox, не викликають Stripe write/read для reconciliation; unauthenticated success працює з нейтральною відповіддю; чужий cancel недоступний; webhook без відвідування success ставить PAID рівно один раз; повторний webhook не додає подію; GET до webhook не заявляє успішну оплату. POST із тією самою спробою зберігає idempotency key та recovery.

Тести: PaymentControllerTest, PaymentControllerIntegrationTest, PaymentLifecycleIntegrationTest, PaymentServiceTest; доповни перевіркою відсутності записів/публікацій і повторних звернень. Не тестуй відсутність мутації лише через HTTP 200. Не роби великий поділ PaymentService і не змінюй cancel/refund політику.

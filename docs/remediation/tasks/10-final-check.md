# 10 — Приймання змін

Модель: GPT-5.6 Terra / medium. Передумова: PASS кроків 01–08, baseline проблеми розв'язані; 09 має PASS або обґрунтований SKIPPED.

1. Прочитай STATUS і diff змін цієї серії. Розрізняй початкові користувацькі зміни та роботу виконавців. Не повторюй аудит усіх файлів.
2. Перевір виконання критеріїв JWT, email/міграції, HTTP-статусів і GET-семантики. Перевір, що тести не просто змінені під нову помилкову поведінку.
3. Один раз запусти `mvn clean verify`. Витягни точні tests/failures/errors/skipped і фактичні LINE/BRANCH з нового JaCoCo. Не змінюй поріг 60%. Після обґрунтованого виправлення нового дефекту повтори відповідні тести та фінальний verify.
4. Переконайся, що проходять BookingOverlapConstraintIntegrationTest, BookingCheckoutIntegrationTest, OutboxMultiInstanceIntegrationTest, KafkaEventDeduplicationIntegrationTest, PaymentLifecycleIntegrationTest, AccommodationQueryCountIntegrationTest і LiquibaseMigrationIntegrationTest у повному suite.
5. Онови тільки релевантні секції README.md і, якщо README-UA.md є актуальним перекладом, відповідні секції в ньому: профілі, required env, JWT lookup, email, HTTP-коди, landing callback, доставка at-least-once. Не переписуй існуючу діаграму без зміни потоку.
6. Запиши незакриті умовні/deferred пункти, результати й дату в STATUS. Не проголошуй production-ready тільки через зелений build.

Готово: verify зелений на остаточному дереві, немає непояснених пропусків важливих тестів, документація відповідає контрактам, усі заяви мають свіжі докази. Якщо є дефект — вузька передача відповідній моделі. Sol high потрібен лише для невирішеної проблеми міграції, безпеки чи конкурентності; повторне повне рев'ю сильною моделлю не обов'язкове.

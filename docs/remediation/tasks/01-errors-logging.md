# 01 — Діагностика 500

Модель: GPT-5.6 Luna / medium. Передумова: виконано діагностику 00.

Початкові файли: GlobalExceptionHandler.java, infrastructure/observability/CorrelationFilter.java, FlowTelemetry.java, SafeFailure (знайти через rg), тести ReadableLoggingTest і StructuredLoggingTest.

Додай ERROR-лог до handleUnhandledException з HTTP-методом, URI без query string, доступним correlationId з наявного MDC і діагностичним stack trace. Публічна відповідь має залишитися загальною. Використовуй стиль логування проєкту, не створюй другий механізм кореляції.

Не логуй тіло запиту, Authorization, cookie чи параметри URL. Перевір наявну практику маскування інтеграційних винятків: TelegramBotClient навмисно не зберігає cause з токеном у URL. Не прибирай цей захист. Якщо довільні cause з інтеграцій потребують окремого sanitizer, передай це Terra, не додавай складний redaction-framework у цій картці.

Поведінкова перевірка: непередбачений безпечний тестовий виняток дає 500 з незмінною загальною відповіддю; ERROR містить його stack trace і шлях; тестовий секрет у header/query не потрапляє до логу. Очікувані 400/403/404 не перетворюються на ERROR цього обробника.

Запусти новий тест хендлера та релевантні logging-тести. Збережи незакомічені правки ReadableLoggingTest. Готово: сценарії проходять, відповідь не розкриває виняток, зміна не зачіпає платіжну логіку.

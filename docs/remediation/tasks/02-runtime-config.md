# 02 — Профілі й обов'язкові параметри запуску

Модель: GPT-5.6 Luna / medium. Передумова: 00.

Файли: application.yml, application-dev.yml, docker-compose.yml, .env.sample, StripeProperties.java, TelegramProperties.java, конфігураційні тести і відповідні секції README.md. Не читати реальний .env для підстановки його значень у файли.

Рішення:

- Прибрати неявний default dev і compose fallback dev. Режим dev лишити явним вибором для локального запуску.
- У звичайному запуску show-sql=false, org.hibernate.SQL та org.hibernate.orm.jdbc.bind не DEBUG/TRACE. Діагностичний dev-профіль збережи й опиши.
- Прибрати працездатні фіктивні defaults Stripe key, Telegram token/chat id; залишити порожні defaults з наявною валідацією або явні required placeholders. Відомі шаблонні значення sk_test_replace_me, replace-with-bot-token, replace-with-chat-id не повинні проходити startup validation. Не вигадуй суворий regex для справжніх секретів.
- Для звичайного запуску вимагати DB_PASSWORD; не переносити booking_password як прихований production fallback. У .env.sample залишити явний шаблон локального налаштування і пояснення заміни. Не додавати реальні секрети.
- Зберегти чинну валідацію JWT і webhook secret. Не додавати мережеву перевірку ключів під час старту.

Перевірки: вузькі тести конфігурації доводять, що dev не активується сам, SQL bind TRACE вимкнений без dev, відсутні/відомі фіктивні інтеграційні значення відхиляються. Явно сконфігурований test/dev-контекст проходить з тестовими значеннями. Не виводити повний `docker compose config`: він може розкрити .env.

Готово: потрібні env-параметри й явний запуск dev описані в README/.env.sample; наявні тести не залежать від вилучених production defaults. Якщо перевірка всієї конфігурації потребує великої перебудови тестів, передати конкретну проблему Terra.

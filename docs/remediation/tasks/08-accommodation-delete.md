# 08 — Зрозумілий конфлікт видалення

Модель: GPT-5.6 Luna / medium. Передумова: 05.

Файли: GlobalExceptionHandler.java, AccommodationService.java і repository (читання), AccommodationControllerIntegrationTest, handler-тести.

Мінімальне рішення: при порушенні саме fk_bookings_accommodation повертати 409 із повідомленням «Accommodation cannot be deleted because it has bookings». Інші DataIntegrityViolation не маскувати під цю причину. Скористайся наявним підходом визначення constraint або точним constraint name від драйвера.

Попередній exists-запит не потрібен як єдиний захист: він має гонку. Якщо додаєш його для бізнесового повідомлення, обробка конкретного FK все одно обов'язкова. Не додавай cascade delete, не видаляй історію бронювань і не вводь soft delete у цій задачі.

Критерії: вільне помешкання видаляється; помешкання з бронюванням дає зрозумілий 409, обидва записи лишаються; missing id зберігає 404; інша FK/unique помилка не отримує повідомлення про бронювання. Перевір скасоване бронювання: воно теж зберігає FK й блокує фізичне видалення.

Запусти AccommodationControllerIntegrationTest і вузький handler-тест. Якщо потрібне складне міжшарове перехоплення транзакції, передай Terra замість широкої перебудови.

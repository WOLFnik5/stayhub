# 07 — Невелике прибирання

Модель: GPT-5.6 Luna / low. Передумова: 06.

Файли: pom.xml, PaymentService.java, AccommodationService.java та лише прямо пов'язані тести.

1. Повторно перевір `rg` для викликів completePayment. Якщо приватний метод усе ще недосяжний — видали.
2. Перевір decreaseAvailability/increaseAvailability і приватні helper-и, що потрібні тільки їм. Якщо немає production-викликів, конфігураційних/reflection посилань і задокументованого контракту — видали методи та лише їхні прямі тести. Не змінюй модель availability або алгоритм бронювання.
3. Видали testng лише якщо немає імпортів, suite XML і залежної конфігурації. Видали прямий liquibase-core, залиш spring-boot-starter-liquibase. Версії залежностей не оновлюй.
4. attachOrRecoverSession та confirmPayment не вважай мертвими за назвою: вони можуть лишатися потрібними закриттю checkout. Перевір виклики після кроку 06.

Готово: production компілюється, PaymentServiceTest і AccommodationServiceTest проходять, міграційний/context тест підтверджує наявність Liquibase через starter. Нові тести для самого факту видалення не потрібні. Не перейменовуй RepositoryImpl і не рефакторь listener-и в цьому патчі.

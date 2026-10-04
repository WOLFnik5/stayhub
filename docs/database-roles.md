# Ролі PostgreSQL і запуск міграцій

Compose використовує три окремі логіни з різними паролями:

| Роль за замовчуванням | Призначення | Права |
| --- | --- | --- |
| `booking_admin` | Початкове налаштування та адміністрування PostgreSQL | Superuser; API не отримує пароль |
| `booking_migrator` | Окремий процес `booking-migrate` | Власник схеми й таблиць; створення об'єктів та trusted extensions; без superuser/керування ролями |
| `booking_runtime` | API, outbox і споживач подій | CONNECT, USAGE схеми/послідовностей, SELECT/INSERT/UPDATE/DELETE бізнес-таблиць |

Runtime не може створювати чи видаляти таблиці, виконувати TRUNCATE, створювати
ролі або читати/змінювати історію Liquibase у схемі `liquibase`.
Майбутні таблиці, створені міграційною роллю у `public`, автоматично отримують
runtime-права через `ALTER DEFAULT PRIVILEGES`. Міграції мають створювати
бізнес-об'єкти саме від імені цієї ролі. Це обмежує права на схему, але runtime
все ще може змінювати бізнес-дані відповідно до своїх DML-повноважень.

## Нова база

1. У `.env` заповніть `POSTGRES_ADMIN_PASSWORD`, `LIQUIBASE_PASSWORD` і
   `DB_PASSWORD` різними паролями. Назви ролей теж мають відрізнятися.
2. Запустіть `docker compose up --build -d`.
3. PostgreSQL виконає `ops/postgres/001-provision-roles.sh` під час ініціалізації
   нового volume. Контейнер `booking-migrate` застосує changelog і завершиться
   з кодом 0. Лише після цього стартує API з Hibernate `validate`.

API примусово отримує `SPRING_LIQUIBASE_ENABLED=false`; його середовище містить
порожні `LIQUIBASE_PASSWORD` та `POSTGRES_ADMIN_PASSWORD` замість секретів із
`.env`. Міграційний контейнер отримує лише свої три `LIQUIBASE_*` змінні та не
запускає вебсервер, Kafka чи фонові задачі застосунку.

Для локального запуску API після `docker compose up -d postgres kafka`
виконайте `docker compose run --build --rm booking-migrate`, а потім
`mvn spring-boot:run` із runtime-змінними середовища, налаштованими в терміналі
або IDE. Maven не зчитує `.env` автоматично.

Для запуску міграцій без Compose спочатку підготуйте ролі й схеми та зберіть
застосунок командою `mvn package`. У середовищі окремого процесу задайте
`LIQUIBASE_URL`, `LIQUIBASE_USERNAME`, `LIQUIBASE_PASSWORD`, а потім виконайте:

```bash
java -Dloader.main=com.bookingapp.infrastructure.migration.DatabaseMigration \
  -cp target/booking-app-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

## Перехід наявного volume

Init-скрипти PostgreSQL не повторюються для вже створеного volume. Самої зміни
`.env` недостатньо. Не видаляйте volume для переходу.

1. Створіть резервну копію та перевірте її відновлення. Зупиніть усі інстанси
   API/воркери, що працюють із цією БД.
2. Залиште `POSTGRES_ADMIN_USER` рівним **наявній адміністративній ролі**,
   створеній попереднім `POSTGRES_USER` (наприклад, старому `booking_user`).
   `POSTGRES_ADMIN_PASSWORD` має містити її чинний пароль. Нове значення
   змінної не змінює пароль уже створеної ролі.
3. Задайте нові окремі `DB_USERNAME=booking_runtime` та
   `LIQUIBASE_USERNAME=booking_migrator` з різними новими паролями.
4. Пересоздайте тільки контейнер PostgreSQL, зберігши volume:

   ```bash
   docker compose up -d --no-deps postgres
   docker compose exec postgres bash /docker-entrypoint-initdb.d/001-provision-roles.sh
   ```

5. Перевірте успішне завершення скрипту. Він транзакційно змінює власників
   об'єктів, прибирає адміністративні права та успадковані ролі у двох нових
   логінів, переносить старі `public.databasechangelog*` до схеми `liquibase`
   і надає DML-доступ до наявних таблиць. Дані та ідентифікатори changeset
   зберігаються. Якщо історія є одночасно в обох схемах, скрипт зупиниться до
   змін: спочатку встановіть походження цих двох історій.
6. Виконайте `docker compose run --build --rm booking-migrate`. Після успіху
   запустіть API з новими runtime-обліковими даними та перевірте health і
   створення/читання бронювання.

Повторний запуск скрипту придатний для відновлення цих прав і зміни паролів
runtime/migrator. Зміни паролів потребують узгодженого оновлення середовища
та перезапуску клієнтів. Скрипт розрахований на виділену БД цього проєкту:
він передає міграційній ролі всі таблиці `public`, а не лише відомий перелік.

## Деплой і відкат

`ops/deployment/deploy.sh` завантажує один immutable image для API та міграцій,
очікує PostgreSQL/Kafka, запускає новий міграційний процес і тільки після
його успіху замінює API. Помилка міграції залишає попередній образ API.
Міграції можуть частково застосуватися до помилки, тому зміни схеми мають бути
сумісними з поточним API. Відкат образу не відкочує схему БД.

Повернення до старого API, який запускав Liquibase у `public`, потребує
окремого плану сумісності: runtime-роль більше не має прав на міграції, а
службові таблиці переміщені. Не повертайте superuser-права API як звичайний
спосіб відкату.

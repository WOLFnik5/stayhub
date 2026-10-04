# 03 — Актуальні права на кожному JWT-запиті

Модель: GPT-5.6 Terra / medium. Передумова: 01 і придатний baseline.

Файли: infrastructure/security/JwtAuthenticationFilter.java, JwtTokenService.java, AuthenticatedUserPrincipal.java, UserRepositoryImpl.java, UserService.java (читання контракту), security/auth/user інтеграційні тести.

Обране мінімальне рішення: перевіряти користувача в БД після перевірки підпису й строку JWT; не додавати refresh token, blacklist, tokenVersion або кеш у цій задачі.

1. Ідентифікуй користувача за userId із валідованого JWT. Authorities, email і principal будуй за поточним записом БД. Роль і старий email із claims не є поточним джерелом прав.
2. Якщо користувача немає — 401. Після commit зміни ролі наступний запит зі старим JWT має використовувати нову роль. Це не гарантує відкликання вже виконуваного запиту.
3. Звузь catch у JwtAuthenticationFilter: зараз він охоплює filterChain.doFilter й може маскувати downstream-помилки як invalid JWT. Перевірку токена відділи від запуску решти ланцюга. Збій БД не має стати 401 «невірний токен» або допустити fallback до claims; забезпеч оброблений 5xx і діагностику через придатний для фільтра механізм. Не припускай, що MVC advice автоматично ловить винятки до DispatcherServlet.
4. Публічні запити без Authorization продовжують працювати без lookup користувача.

Критерії: старий ADMIN JWT після demotion отримує 403 на admin endpoint; JWT відсутнього користувача — 401 (видалення імітувати репозиторієм або чистим test fixture, не додавати DELETE API); актуальний ADMIN має доступ; bad/expired JWT — 401; інфраструктурний/ downstream-збій не маскується як 401; зміна email у профілі не ламає ідентифікацію за id.

Запусти JwtTokenServiceTest, нові filter-тести, AuthControllerIntegrationTest, UserControllerIntegrationTest і релевантні захищені endpoint-тести. Онови README: один lookup на автентифікований запит, актуальні права, TTL не є механізмом миттєвого відкликання. Не зберігай хеш пароля в principal.

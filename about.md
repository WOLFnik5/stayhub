# Опис компонентів StayHub Booking Service

Цей документ описує production-код у `src/main/java/com/bookingapp`. Назви методів
залишено такими, як у коді; опис подано українською. Конструктори, створені Lombok
(`@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`), не
перелічено окремо. У `record` Java автоматично створює конструктор, методи-доступи
з назвою компонента (`id()`, `email()` тощо), а також `equals`, `hashCode` і
`toString`.

## Точка входу та домен

| Тип | Призначення та методи |
| --- | --- |
| `BookingAppApplication` | Точка входу Spring Boot. `main(String[] args)` запускає застосунок. |
| `Accommodation` | Доменна модель помешкання: `id`, `type`, `location`, `size`, `amenities`, `dailyRate`, `availability`. Lombok генерує `get…`/`set…` для кожного поля. |
| `Booking` | Доменна модель бронювання: ідентифікатор, дати, ідентифікатори помешкання й користувача, статус. Має Lombok-гетери/сетери. |
| `Payment` | Доменна модель платіжної спроби: статус, бронювання, Stripe session, сума, валюта й час створення. Окрім Lombok-методів має скорочений конструктор без `currency` і `createdAt`, який встановлює `usd` та поточний час. |
| `User` | Доменна модель користувача: `id`, email, ім'я, прізвище, пароль, роль; має Lombok-гетери/сетери. |
| `PageResult<T>` | Результат посторінкового читання: `content`, `page`, `size`, `totalElements`. `totalPages()` обчислює кількість сторінок. |
| `AccommodationType` | Перелік типів помешкання. |
| `BookingStatus` | Перелік станів бронювання, зокрема активний, скасований і прострочений. |
| `PaymentStatus` | Перелік станів платіжної спроби. |
| `UserRole` | Ролі доступу: `CUSTOMER` та `ADMIN`. |

### Події домену

`AccommodationCreatedEvent`, `BookingCreatedEvent`, `BookingCanceledEvent`,
`BookingExpiredEvent` і `PaymentSucceededEvent` — незмінні `record`-повідомлення
для Kafka. Вони передають ідентифікатори агрегатів, потрібні бізнес-дані (тип,
дати, суму або статус) та час виникнення події. Їхні методи — стандартні
component-accessors record, наприклад `bookingId()` чи `occurredAt()`.

## HTTP-шар

### Контролери

| Клас | Публічні методи |
| --- | --- |
| `AccommodationController` | `createAccommodation` створює помешкання; `listAccommodations` повертає сторінку доступних; `getAccommodationById` повертає деталі; `updateAccommodation` повністю замінює дані; `patchAccommodation` змінює передані поля; `deleteAccommodation` видаляє запис. |
| `BookingController` | `createBooking` створює бронювання; `listBookings` повертає бронювання поточного користувача або адміністративний список за фільтром; `listMyBookings` — власні; `getBookingById` — деталі; `updateBooking` і `patchBooking` змінюють дати; `cancelBooking` скасовує бронювання. |
| `PaymentController` | `getPayments` читає платежі; `createPayment` створює або повторно використовує Checkout-сесію; `handlePaymentSuccess` обробляє success callback; `paymentCancelReturn` повертає нейтральне повідомлення після скасування Checkout; `handlePaymentCancel` визначає стан скасованої сесії. |
| `StripeWebhookController` | `receive` перевіряє підпис Stripe, приймає успішні події Checkout і передає їх `PaymentService`; помилку домену повертає як `503`, щоб Stripe повторив доставлення. |
| `AuthController` | `register` перевіряє rate limit і реєструє користувача; `login` перевіряє rate limit і повертає JWT. |
| `UserController` | `updateUserRole` змінює роль (адміністратор); `getCurrentUserProfile`, `updateCurrentUserProfile`, `patchCurrentUserProfile` працюють із профілем поточного користувача. |
| `HealthController` | `health` повертає `HealthResponse` зі станом сервісу. |

### DTO

Усі DTO нижче — `record`, тому їхні поля водночас є методами-доступами.

| Група | Типи та компоненти |
| --- | --- |
| Помешкання | `CreateAccommodationRequest` і `UpdateAccommodationRequest`: `type`, `location`, `size`, `amenities`, `dailyRate`, `availability`; `PatchAccommodationRequest` містить ті самі необов'язкові поля. `AccommodationListResponse` містить усі поля, крім amenities; `AccommodationDetailResponse` містить усі; `AccommodationSummaryResponse` — `id`, `type`, `location`, `size`. |
| Бронювання | `CreateBookingRequest`: `accommodationId`, `checkInDate`, `checkOutDate`; `UpdateBookingRequest` має обов'язкові дати, `PatchBookingRequest` — необов'язкові. `BookingResponse` містить ідентифікатор, дати, помешкання, користувача, статус; `BookingDetail` поєднує доменні `Booking` і `Accommodation`; `BookingDetailResponse` додає `AccommodationSummaryResponse`. |
| Оплати | `CreatePaymentRequest(bookingId)`; `PaymentSessionResult` передає дані Checkout-сесії; `PaymentResponse` — збережену платіжну спробу; `PaymentCancelResult` — внутрішній результат скасування; `PaymentCancelResponse` — його HTTP-подання; `PaymentSuccessResponse` містить повідомлення та оплату. |
| Автентифікація та профіль | `RegisterRequest`, `LoginRequest`; внутрішній `AuthenticationResult`; зовнішній `AuthResponse`; `UpdateCurrentUserRequest` і `PatchCurrentUserRequest`; `UpdateUserRoleRequest`; `UserProfileResponse`. Вони містять відповідні дані користувача, токена та ролі. |
| Загальні | `PageResponse<T>` містить контент і параметри пагінації; `ApiErrorResponse` — час, HTTP-статус, помилку, повідомлення і шлях; `HealthResponse(status)` — стан сервісу. |

Вхідні DTO мають Jakarta Validation-анотації: email перевіряється як email,
текстові значення — на непорожність, сума й доступність — на невід'ємність,
а дати бронювання — на актуальність.

### Мапери (інтерфейси MapStruct)

| Інтерфейс | Методи |
| --- | --- |
| `AccommodationWebMapper` | `toListResponse(Accommodation)`, `toDetailResponse(Accommodation)` перетворюють доменну модель на HTTP-відповідь. |
| `BookingWebMapper` | `toResponse(Booking)`, `toDetailResponse(BookingDetail)`, `toFilterQuery(Long, BookingStatus)` будують відповідь і фільтр. |
| `PaymentWebMapper` | `toResponse(Payment)`, `toResponse(PaymentSessionResult)`, `toCancelResponse(PaymentCancelResult)`, `toSuccessResponse(Payment)`, `toFilterQuery(Long)` перетворюють результати платежу. |
| `AuthWebMapper` | `toResponse(AuthenticationResult)` формує відповідь з bearer-токеном. |
| `UserWebMapper` | `toResponse(User)` формує безпечне подання профілю без пароля. |

## Прикладні сервіси

| Клас | Публічні методи |
| --- | --- |
| `AccommodationService` | `createAccommodation`, `getAccommodationById`, `listAccommodations`, `updateAccommodation`, `patchAccommodation`, `deleteAccommodation`; `decreaseAvailability`/`increaseAvailability` безпечно змінюють залишок. Внутрішньо валідує тип, текст, зручності, ціну, кількість і межі пагінації. |
| `BookingService` | `createBooking` перевіряє доступність, дати й перетини; `getBookingDetail`, `getBookingById`; `listBookings`, `listMyBookings`; `updateBooking`, `patchBooking`; `cancelBooking`. Перевіряє доступ поточного користувача, дозволені стани та блокує запис для конкурентного оновлення. |
| `PaymentService` | `createPaymentSession` створює/reuse Stripe Checkout; `getPayments`; `handlePaymentSuccess` і `handleWebhookSuccess` підтверджують платіж; дві версії `handlePaymentCancel` формують результат за сесією та/або бронюванням; `closeCheckoutForBooking` закриває неоплачені сесії під час скасування чи завершення бронювання. Внутрішньо звіряє суму, валюту, доступ і статуси. |
| `AuthService` | `register` створює CUSTOMER, хешує пароль і видає JWT; `login` перевіряє облікові дані й видає JWT. |
| `UserService` | `getCurrentUserProfile`; `updateCurrentUserProfile`; `patchCurrentUserProfile`; `updateUserRole`. Перевіряє унікальність email і валідність полів. |
| `BookingExpirationService` | `expireBookings(LocalDate)` знаходить прострочені бронювання, запускає транзакційну обробку кожного та повертає `BookingExpirationResult` (кількість, успішні та помилкові ID). |
| `BookingExpirationTransactionService` | `expireIfEligible(Long, LocalDate)` під блокуванням переводить придатне бронювання в `EXPIRED`, закриває Checkout і ставить подію. |
| `BookingExpirationResult` | Record результату завершення: `expiredCount`, `expiredBookingIds`, `failedBookingIds`; має також скорочений конструктор без переліку помилок. |
| `BookingExpirationScheduler` | `expireBookingsDaily()` викликає сервіс за cron-розкладом. |
| `BookingValidationUtils` | Статичний `validateBookingDates` відхиляє некоректний або порожній діапазон дат. |
| `TextValidationUtils` | Статичний `requireNonBlank` повертає перевірений текст або кидає помилку; `selectNonBlank` обирає нове непорожнє значення чи наявне. |

## Збереження даних

| Клас / інтерфейс | Методи та роль |
| --- | --- |
| `AccommodationRepositoryImpl` | `save`, `findById`, `findByIdForUpdate`, `findAvailablePage`, `existsById`, `deleteById`. Пагінація спочатку читає ID, потім сутності зі зручностями, запобігаючи N+1. |
| `BookingRepositoryImpl` | `save`, `findById`, `findByIdForUpdate`, `findAllByFilter`, `findAllByUserId`, `countActiveBookingOverlaps`, `findBookingsToExpire`. Останні два підтримують правила перетинів і завершення бронювань. |
| `PaymentRepositoryImpl` | `save`, `findById`, `findByBookingId`, `findBySessionId`, `findAllByBookingId`, `findAllByFilter`, `flush`, `refresh`. |
| `UserRepositoryImpl` | `save`, `findById`, `findByEmail`, `existsByEmail`. |
| `BookingFilterQuery`, `PaymentFilterQuery` | Незмінні фільтри запитів за користувачем та, для бронювання, статусом. |
| `AccommodationPersistenceMapper`, `BookingPersistenceMapper`, `PaymentPersistenceMapper`, `UserPersistenceMapper` | MapStruct-інтерфейси з `toEntity(domain)` і `toDomain(entity)` для кожної пари доменна модель/JPA-сутність. |
| `AccommodationEntity`, `BookingEntity`, `PaymentEntity`, `UserEntity` | JPA-відображення таблиць. Lombok генерує їхні гетери/сетери; `AccommodationEntity` зберігає колекцію зручностей. |
| `ProcessedEventId` | Складений ключ inbox: event ID і consumer. |
| `ProcessedEventEntity` | JPA-запис про вже оброблену подію для дедуплікації; має Lombok-доступи. |
| `ProcessedEventJpaRepository` | Spring Data JPA-репозиторій inbox; успадковує CRUD-методи, а також `existsById` і `save`. |

### Transactional outbox

| Клас / інтерфейс | Методи та роль |
| --- | --- |
| `OutboxEventEntity` | `newEvent` створює запис у стані `NEW`; `markProcessing`, `incrementAttempts`, `markSent`, `markFailed`, `markDead` керують життєвим циклом події та lease. Lombok надає доступ до її полів. |
| `OutboxStatus` | Стани outbox: `NEW`, `PROCESSING`, `SENT`, `FAILED`, `DEAD`. |
| `OutboxEventJpaRepository` | Успадковує CRUD; `summarizeStatuses` повертає агрегати за статусом; `findBatchForProcessing` захоплює пакет `FOR UPDATE SKIP LOCKED`; `releaseStaleClaims`, `markSentIfClaimOwned`, `markFailedIfClaimOwned` змінюють стан лише за коректного lease; `deleteByStatusAndPublishedAtBefore` очищує старі події. Вкладений `StatusSummary` має `getStatus`, `getTotal`, `getOldest`. |
| `OutboxTransactionService` | `claimBatch`, `markSent`, `markFailed`, `recoverStaleClaims`, `cleanupSentEvents` виконують транзакційні зміни outbox. |
| `OutboxKafkaPublisher` | `publishPendingEvents` відправляє захоплені події до Kafka; `recoverStaleClaims` і `cleanupSentEvents` обслуговують чергу за розкладом. |

## Інфраструктура

### Kafka, Telegram і Stripe

| Тип | Методи та роль |
| --- | --- |
| `KafkaEventPublisher` | Контракт: `publishAccommodationCreated`, `publishBookingCreated`, `publishBookingCanceled`, `publishBookingExpired`, `publishPaymentSucceeded`. |
| `OutboxKafkaEventPublisher` | Реалізація контракту: серіалізує доменну подію й зберігає її в outbox у межах поточної транзакції. |
| `TelegramEventConsumer` | `consumeBookingCreated`, `consumeBookingCanceled`, `consumeAccommodationCreated`, `consumePaymentSucceeded`, `consumeBookingExpired` — Kafka listeners. Кожен дедуплікує event ID, десеріалізує payload і надсилає Telegram-повідомлення. |
| `KafkaEventDeduplicationService` | `isProcessed(UUID, String)` перевіряє inbox; `markProcessed(UUID, String)` фіксує успішну обробку. |
| `TelegramNotificationService` | Контракт для `sendMessage`, а також `notifyAccommodationCreated`, `notifyBookingCreated`, `notifyBookingCanceled`, `notifyAccommodationReleased`, `notifyPaymentSuccessful`, `notifyNoExpiredBookingsToday`. |
| `TelegramNotificationClient` | Реалізує контракт, форматуючи бізнес-об'єкти та делегуючи надсилання клієнту бота. |
| `TelegramMessageFormatter` | `format…`-методи створюють текст для кожного доменного об'єкта й кожного Kafka-event (`Created`, `Canceled`, `Expired`, `Succeeded`). |
| `TelegramBotClient` | `sendMessage(String)` викликає Telegram Bot API; внутрішній overload додає контекст трасування. |
| `StripePaymentProvider` | `createPaymentSession` створює Stripe Checkout session; `isPaymentSuccessful`, `isPaymentSessionActive`, `isPaymentSessionExpired`, `validatePayment`, `expireUnpaidSession` перевіряють і закривають її. Клас ізолює Stripe SDK від сервісу оплат. |

### Безпека

| Тип | Методи та роль |
| --- | --- |
| `CurrentUserService` | Контракт `getCurrentUser()` для отримання автентифікованого користувача. |
| `AuthenticatedCurrentUserService` | Реалізація: читає Spring Security context і повертає `CurrentUser`; за відсутності principal кидає помилку автентифікації. |
| `CurrentUser` | Record із `id`, email і роллю; accessors record. |
| `AuthenticatedUserPrincipal` | Record JWT-principal; `authorities()` перетворює роль на Spring `GrantedAuthority`. |
| `JwtTokenService` | `generateToken(User)` створює JWT; `parsePrincipal(String)` перевіряє токен і створює principal. |
| `JwtAuthenticationFilter` | Перевизначає `doFilterInternal`: читає `Bearer` токен, заповнює SecurityContext або формує 401. |
| `AuthRateLimiter` | `checkLogin` і `checkRegister` рахують спроби по IP у часовому вікні та кидають `RateLimitExceededException` при перевищенні. |
| `RestAuthenticationEntryPoint` | `commence` повертає JSON-помилку 401. |
| `RestAccessDeniedHandler` | `handle` повертає JSON-помилку 403. |
| `SecurityConfiguration` | `securityFilterChain` описує правила доступу та додає JWT-фільтр; `passwordEncoder` повертає BCrypt encoder. |
| `SecurityErrorResponse` | Record уніфікованої відповіді помилки безпеки. |

### Спостережуваність і конфігурація

| Тип | Методи та роль |
| --- | --- |
| `CorrelationContext` | `open`, `normalize`, `currentOrNew`, `close` керують correlation/event ID у MDC. |
| `CorrelationFilter` | `doFilterInternal` приймає або створює `X-Correlation-ID`, додає його у відповідь і контекст. |
| `FlowTracing` | `start`, `resume`, `headers` створюють/продовжують OpenTelemetry span і переносять trace headers. Вкладений `TraceScope` має `tag`, `error`, `close`. |
| `FlowTelemetry` | `record` записує тривалість/результат етапу; `count` інкрементує лічильник. |
| `OutboxMetrics` | `refresh` збирає кількості outbox за статусами та вік найстаршої події. |
| `TelegramRecordInterceptor` | `openContext`, `success`, `header` встановлюють correlation/tracing-контекст Kafka-запису. |
| `SafeFailure` | `describe(Throwable)` формує безпечну назву помилки для логів і метрик. |
| `JacksonConfiguration` | `objectMapper()` створює налаштований Jackson `ObjectMapper`. |
| `KafkaProducerConfiguration` | `producerFactory` створює producer factory; `kafkaTemplate` — шаблон публікації Kafka. |
| `KafkaConsumerConfiguration` | `telegramConsumerFactory` створює consumer factory; `telegramKafkaListenerContainerFactory` налаштовує listener, interceptor і retry-обробник. |
| `StripeConfiguration` | `stripeClient(StripeProperties)` створює Stripe SDK client. |
| `TelegramConfiguration` | `telegramRestClient(TelegramProperties)` створює HTTP-клієнт Telegram API. |
| `OpenApiConfiguration` | `bookingAppOpenApi()` формує OpenAPI-опис і JWT-схему. |
| `SchedulingConfiguration`, `MapStructConfig` | Маркерні конфігураційні типи для увімкнення scheduler та спільних правил MapStruct. |
| `JwtProperties`, `StripeProperties`, `TelegramProperties`, `KafkaTopicsProperties`, `AuthRateLimitProperties` | `@ConfigurationProperties` класи з Lombok-гетерами/сетерами для зовнішніх налаштувань. |
| `OutboxProperties` | Record конфігурації параметрів outbox; значення доступні через accessors record. |

## Помилки

`DomainException` є базовою runtime-помилкою домену. Від неї успадковуються
`BusinessValidationException`, `BookingConflictException`, `InvalidBookingStateException`,
`PaymentStateException` та `EntityNotFoundDomainException`. `ForbiddenOperationException`
і `RateLimitExceededException` також є runtime-помилками для доступу й ліміту.
Кожна має конструктор із текстом повідомлення.

`GlobalExceptionHandler` перехоплює валідаційні, доменні, security та інші HTTP-помилки
і перетворює їх на `ApiErrorResponse`. Його публічні методи `handleEntityNotFound`,
`handleBusinessValidation`, `handleBookingConflict`, `handleRateLimitExceeded`,
`handleForbiddenOperation`, `handleUnauthorized`, `handleAuthenticationException`,
`handleAccessDenied`, `handleMethodArgumentNotValid`, `handleBindException`,
`handleConstraintViolation`, `handleMissingRequestParameter`,
`handleDataIntegrityViolation` і `handleUnhandledException` відповідають за конкретні
категорії. Приватні `buildResponse`, `formatFieldError` та `extractFieldErrors`
створюють і форматують відповідь.

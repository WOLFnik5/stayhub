# Booking App

Booking App is a Spring Boot backend for managing accommodations, bookings, payments, and operational notifications. It exposes a JWT-secured REST API, stores data in PostgreSQL, publishes business events through an outbox-backed Kafka flow, creates Stripe checkout sessions, and delivers Telegram notifications for selected events.

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Liquibase
- Apache Kafka
- Stripe Java SDK
- Telegram Bot API
- springdoc OpenAPI / Swagger UI
- Maven
- Docker / Docker Compose
- JUnit 5, Mockito, Testcontainers

## Architecture

The project now follows a pragmatic 3-tier layered architecture:

- `com.bookingapp.domain`
  Business core: domain models, enums, exceptions, domain events, repository contracts, business services, and a small set of service DTOs.
- `com.bookingapp.web`
  HTTP layer: controllers, request/response DTOs, web mappers, and API exception handling.
- `com.bookingapp.infrastructure`
  Technical implementation details: persistence, Kafka, outbox publishing, Stripe, Telegram, security, configuration, and scheduler components.

Primary runtime flow:

`Controller -> Service -> Repository / Infrastructure`

Typical examples:

- `web.controller.AuthController -> domain.service.AuthService -> domain.repository.UserRepository + infrastructure.security.JwtTokenService`
- `web.controller.BookingController -> domain.service.BookingService -> domain.repository.BookingRepository + infrastructure.kafka.KafkaEventPublisher`
- `web.controller.PaymentController -> domain.service.PaymentService -> domain.repository.PaymentRepository + infrastructure.stripe.StripePaymentProvider`

Asynchronous event flow:

`Service -> KafkaEventPublisher -> outbox table -> OutboxKafkaPublisher -> Kafka -> TelegramEventConsumer -> TelegramNotificationService`

## Package Responsibilities

### Domain

- `domain.model`
  Immutable-style business entities such as `Accommodation`, `Booking`, `Payment`, and `User`.
- `domain.repository`
  Repository contracts used by services.
- `domain.service`
  Business orchestration and transactional use cases implemented as concrete services.
- `domain.service.dto`
  Small service-level contracts that still add value:
  `AuthenticationResult`, `BookingFilterQuery`, `PaymentFilterQuery`, `PaymentSessionResult`, `PaymentCancelResult`, `BookingExpirationResult`, `CurrentUser`.

### Web

- `web.controller`
  REST endpoints and request handling.
- `web.dto`
  External API request/response DTOs.
- `web.mapper`
  Mapping between domain/service results and API DTOs.
- `web.exception`
  API-facing exception translation.

### Infrastructure

- `infrastructure.persistence`
  Repository implementations, JPA entities, persistence mappers, Spring Data repositories, and outbox persistence.
- `infrastructure.kafka`
  Event publisher contract/implementation, Kafka consumer, and event message formatting.
- `infrastructure.outbox`
  Scheduled outbox delivery to Kafka.
- `infrastructure.stripe`
  Stripe checkout integration.
- `infrastructure.telegram`
  Telegram client, formatting, and notification delivery.
- `infrastructure.security`
  JWT auth, current-user resolution, and Spring Security integration.
- `infrastructure.config`
  Spring Boot configuration and typed properties.
- `infrastructure.scheduler`
  Scheduled booking expiration trigger.

## Features

- Customer registration and login with JWT authentication
- Public accommodation browsing
- Admin accommodation management
- Booking creation, update, cancellation, and listing
- Stripe checkout session creation and payment completion handling
- Kafka event publishing with outbox persistence
- Telegram notifications for selected business events
- Scheduled expiration of stale bookings
- Liquibase schema management

## Running Locally

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL and Kafka, or Docker Compose

### Start the application

```bash
mvn spring-boot:run
```

Default URL:

```text
http://localhost:8080
```

### Docker Compose

```bash
docker compose up --build
```

Useful endpoints:

- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Kafka UI: `http://localhost:8081`

## Testing

The test suite mirrors the layered structure:

- `src/test/java/com/bookingapp/web`
  MVC and controller integration tests
- `src/test/java/com/bookingapp/domain/service`
  service tests
- `src/test/java/com/bookingapp/infrastructure`
  persistence and messaging tests
- `src/test/java/com/bookingapp/integration`
  broader integration coverage
- `src/test/java/com/bookingapp/testsupport`
  shared PostgreSQL and Liquibase test harnesses

Run tests with:

```bash
mvn test
```

## README Architecture Summary

Booking App is organized around three layers: `domain` for business rules and repository contracts, `web` for the HTTP API, and `infrastructure` for persistence, security, messaging, and external integrations. The main request path is `Controller -> Service -> Repository/Infrastructure`, while asynchronous notifications flow through an outbox-backed Kafka pipeline.

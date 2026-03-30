# Accommodation Booking Service

This project is a Java 21 / Spring Boot 4 backend for accommodation booking management. It provides JWT-secured REST APIs for authentication, accommodation catalog management, bookings, payments, and operational notifications, while keeping a layered architecture built around `domain`, `web`, and `infrastructure`.

## Overview

The service supports:

- customer registration and login
- public accommodation browsing
- admin accommodation management
- booking creation, update, cancellation, and listing
- Stripe checkout session creation and payment completion handling
- Kafka-based event publishing through an outbox flow
- Telegram notifications for selected business events
- scheduled booking expiration
- schema management with Liquibase

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
- JUnit 5, Mockito, Testcontainers, JaCoCo

## Architecture Summary

The application keeps the existing 3-layer structure:

- `com.bookingapp.domain`
  Business core: domain models, enums, domain events, repository contracts, business exceptions, and services.
- `com.bookingapp.web`
  HTTP API layer: controllers, request/response DTOs, web mappers, and API exception handling.
- `com.bookingapp.infrastructure`
  Technical adapters: persistence, JPA entities/repositories, security, Kafka, outbox publishing, Stripe, Telegram, configuration, and schedulers.

Main synchronous flow:

`Controller -> Service -> Repository / Infrastructure`

Main asynchronous flow:

`Service -> outbox event persistence -> OutboxKafkaPublisher -> Kafka -> Telegram consumer/notification service`

## Environment Configuration

Secrets and integration settings are externalized through environment variables. Start by copying `.env.sample` to `.env` and adjusting values if needed.

```bash
cp .env.sample .env
```

Important convention used by this project:

- `.env` is the source of truth for local development.
- Local development values target services exposed on the host machine.
- `docker-compose.yml` overrides only the `booking-app` container connection variables that must point to internal Compose service names.

Default local development values in `.env.sample` assume:

- PostgreSQL is reachable at `localhost:5433`
- Kafka is reachable at `localhost:9092`
- the app runs on `localhost:8080`

Required variables you should review before demo/use:

- `JWT_SECRET`
- `STRIPE_SECRET_KEY`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_CHAT_ID`

Database variables:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `POSTGRES_DB`

## Setup Instructions

Prerequisites:

- Java 21
- Maven 3.9+
- Docker Desktop or a compatible Docker engine if you want Compose-based dependencies/runtime

## Local Run

Use this mode when you want to run Spring Boot on your machine and keep PostgreSQL/Kafka externalized.

1. Copy `.env.sample` to `.env`.
2. Start infrastructure dependencies:

```bash
docker compose up postgres kafka kafka-ui -d
```

3. Run the application:

```bash
mvn spring-boot:run
```

The default local configuration from `application.yml` matches the host ports exposed by Docker Compose:

- PostgreSQL: `localhost:5433`
- Kafka: `localhost:9092`
- Application: `http://localhost:8080`

## Docker Compose Run

Use this mode when you want the full stack, including the application, in containers.

```bash
docker compose up --build
```

In Compose mode:

- PostgreSQL runs as `postgres:5432` inside the Compose network
- Kafka runs as `kafka:29092` inside the Compose network
- the `booking-app` container gets those internal addresses from `docker-compose.yml`

Public URLs after startup:

- API base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI docs: `http://localhost:8080/api-docs`
- Custom health endpoint: `http://localhost:8080/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Kafka UI: `http://localhost:8081`

## Health Endpoints

The project exposes:

- `GET /health`
  Public custom health endpoint returning:
  `{"status":"UP"}`
- `GET /actuator/health`
  Public Spring Boot actuator health endpoint

## API Summary

Main business endpoints:

- `POST /auth/register`
  Register a new customer account.
- `POST /auth/login`
  Authenticate and receive a bearer token.
- `GET /accommodations`
  Public accommodation listing.
- `GET /accommodations/{id}`
  Public accommodation details.
- `POST /accommodations`
  Admin-only accommodation creation.
- `PUT /accommodations/{id}`
  Admin-only full accommodation update.
- `PATCH /accommodations/{id}`
  Admin-only partial accommodation update.
- `DELETE /accommodations/{id}`
  Admin-only accommodation deletion.
- `GET /bookings`
  Authenticated booking list with role-based behavior.
- `GET /bookings/{id}`
  Authenticated booking details.
- `POST /bookings`
  Authenticated booking creation.
- `PUT /bookings/{id}`
  Authenticated booking update.
- `PATCH /bookings/{id}`
  Authenticated booking partial update.
- `DELETE /bookings/{id}`
  Authenticated booking cancellation/deletion flow.
- `POST /payments`
  Authenticated payment/checkout session creation.
- `GET /payments/success`
  Public Stripe success callback endpoint.
- `GET /payments/cancel`
  Public Stripe cancel callback endpoint.
- `GET /users/me`
  Authenticated current-user profile endpoint.
- `PUT /users/{id}/role`
  Admin-only role update.

Swagger is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

## Roles and Permissions

- Anonymous users:
  `POST /auth/**`, `GET /health`, `GET /actuator/health`, payment callback endpoints, Swagger/OpenAPI endpoints, and public accommodation reads.
- `CUSTOMER`:
  authenticated booking/payment operations allowed by controller/service rules and access to their own profile.
- `ADMIN`:
  accommodation management and user role management, plus authenticated endpoints available to regular users where applicable.

## Payments and Notifications

Stripe setup notes:

- set `STRIPE_SECRET_KEY`
- verify `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL`
- local defaults point to `http://localhost:8080/payments/success` and `http://localhost:8080/payments/cancel`

Telegram setup notes:

- set `TELEGRAM_BOT_TOKEN`
- set `TELEGRAM_CHAT_ID`

Kafka / eventing notes:

- Kafka is required for the outbox-to-notification flow
- the app publishes business events to Kafka topics configured by environment variables

## Testing and Coverage

Run the full verification pipeline:

```bash
mvn clean verify
```

Run tests only:

```bash
mvn test
```

Coverage:

- JaCoCo report is generated during `verify`
- the HTML report is produced under `target/site/jacoco/index.html`
- the build fails when overall line coverage for project code drops below 60%
- the Spring Boot bootstrap class `BookingAppApplication` is excluded from the JaCoCo gate because it contains only framework startup boilerplate

## Non-Functional Assumptions and Lightweight Verification

This project targets a relatively small workload described in the task requirements:
- up to 5 concurrent users
- up to 1,000 accommodations
- up to 50,000 bookings per year
- approximately 30 MB of business data per year

These targets are addressed by design and supported by a lightweight concurrent smoke test:
- `FiveConcurrentUsersTest` starts the application on a random port
- uses Testcontainers PostgreSQL for isolated execution
- runs 5 concurrent user scenarios
- verifies registration/login and representative public/authenticated endpoints

This test is intended as lightweight engineering verification, not as a formal benchmark or load-certification report.
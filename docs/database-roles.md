# PostgreSQL roles and database migrations

Compose uses three separate logins with distinct passwords:

| Default role | Purpose | Privileges |
| --- | --- | --- |
| `booking_admin` | Initial setup and PostgreSQL administration | Superuser; its password is not passed to the API |
| `booking_migrator` | Separate `booking-migrate` process | Owns schemas and tables; can create objects and trusted extensions; no superuser or role-management privileges |
| `booking_runtime` | API, outbox publisher, and event consumer | CONNECT, schema/sequence USAGE, and SELECT/INSERT/UPDATE/DELETE on business tables |

The runtime role cannot create or drop tables, run TRUNCATE, create roles, or
read or modify Liquibase history in the `liquibase` schema. Future tables
created by the migration role in `public` automatically receive runtime
privileges through `ALTER DEFAULT PRIVILEGES`. Migrations must create business
objects as that role. These restrictions protect the schema; the runtime role
can still modify business data within its DML privileges.

## New database

1. Set `POSTGRES_ADMIN_PASSWORD`, `LIQUIBASE_PASSWORD`, and `DB_PASSWORD` in
   `.env` to distinct passwords. The three role names must also differ.
2. Run `docker compose up --build -d`.
3. PostgreSQL runs `ops/postgres/001-provision-roles.sh` when initializing a new
   volume. The `booking-migrate` container applies the changelog and exits with
   code 0. Only then does the API start with Hibernate in `validate` mode.

The API receives `SPRING_LIQUIBASE_ENABLED=false`. Its environment contains
empty `LIQUIBASE_PASSWORD` and `POSTGRES_ADMIN_PASSWORD` values instead of the
secrets from `.env`. The migration container receives only its three
`LIQUIBASE_*` variables and does not start the application's web server,
Kafka components, or background jobs.

To run the API locally, start dependencies with `docker compose up -d postgres kafka`,
run `docker compose run --build --rm booking-migrate`, and then run
`mvn spring-boot:run` with runtime environment variables configured in your
terminal or IDE. Maven does not load `.env` automatically.

To run migrations without Compose, first provision the roles and schemas and
build the application with `mvn package`. Set `LIQUIBASE_URL`,
`LIQUIBASE_USERNAME`, and `LIQUIBASE_PASSWORD` in the separate process's
environment, then run:

```bash
java -Dloader.main=com.bookingapp.infrastructure.migration.DatabaseMigration \
  -cp target/booking-app-0.0.1-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

## Transitioning an existing volume

PostgreSQL initialization scripts do not run again for an existing volume.
Changing `.env` alone is insufficient. Do not delete the volume to transition.

1. Create a backup and verify that it can be restored. Stop every API instance
   and worker connected to this database.
2. Keep `POSTGRES_ADMIN_USER` set to the **existing administrator role** created
   by the previous `POSTGRES_USER`, such as the old `booking_user`.
   `POSTGRES_ADMIN_PASSWORD` must contain that role's current password.
   Changing an environment variable does not change an existing role's password.
3. Set new, separate `DB_USERNAME=booking_runtime` and
   `LIQUIBASE_USERNAME=booking_migrator` logins with distinct new passwords.
4. Recreate only the PostgreSQL container, retaining its volume:

   ```bash
   docker compose up -d --no-deps postgres
   docker compose exec postgres bash /docker-entrypoint-initdb.d/001-provision-roles.sh
   ```

5. Verify that the script completed successfully. It transactionally transfers
   object ownership, removes administrator privileges and inherited role
   memberships from the two application logins, moves the old
   `public.databasechangelog*` tables to `liquibase`, and grants DML access to
   existing tables. Data and changeset identifiers are preserved. If migration
   history exists in both schemas, the script stops before making changes;
   establish the provenance of both histories first.
6. Run `docker compose run --build --rm booking-migrate`. After it succeeds,
   start the API with the new runtime credentials and verify health checks and
   booking creation and retrieval.

The provisioning script can be rerun to restore these privileges or rotate
runtime/migration passwords. Password rotation requires coordinated
environment updates and client restarts. The script assumes a database
dedicated to this project: it transfers all tables in `public` to the migration
role, rather than limiting changes to a fixed list.

## Deployment and rollback

`ops/deployment/deploy.sh` pulls the same immutable image for the API and
migrations, waits for PostgreSQL/Kafka, runs a fresh migration process, and
replaces the API only after migrations succeed. A migration failure leaves the
previous API image in place. Some migrations may complete before a later
failure, so schema changes must remain compatible with the running API.
Rolling back an image does not roll back the database schema.

Returning to an older API that ran Liquibase in `public` requires a separate
compatibility plan: the runtime role no longer has migration privileges and
the metadata tables have moved. Do not restore superuser privileges to the API
as a routine rollback procedure.

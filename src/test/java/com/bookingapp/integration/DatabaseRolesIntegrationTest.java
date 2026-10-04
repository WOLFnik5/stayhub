package com.bookingapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.infrastructure.migration.DatabaseMigration;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class DatabaseRolesIntegrationTest {
    private static final String RUNTIME_PASSWORD = "runtime'test-password";
    private static final String MIGRATION_PASSWORD = "migration-test-password";
    private static final String SCRIPT = "/docker-entrypoint-initdb.d/001-provision-roles.sh";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:17-alpine")
            .withDatabaseName("booking_app_test")
            .withUsername("booking_admin")
            .withPassword("admin-test-password")
            .withEnv("DB_USERNAME", "booking_runtime")
            .withEnv("DB_PASSWORD", RUNTIME_PASSWORD)
            .withEnv("LIQUIBASE_USERNAME", "booking_migrator")
            .withEnv("LIQUIBASE_PASSWORD", MIGRATION_PASSWORD)
            .withCopyFileToContainer(MountableFile.forHostPath(
                    Path.of("ops/postgres/001-provision-roles.sh").toAbsolutePath(), 0755), SCRIPT);

    private static JdbcTemplate runtime;
    private static JdbcTemplate migration;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        runtime = jdbc("booking_runtime", RUNTIME_PASSWORD);
        migration = jdbc("booking_migrator", MIGRATION_PASSWORD);
        migrate();
    }

    @Test
    void runtimeHasOnlyDataPrivilegesAndCannotAssumePrivilegedRoles() {
        assertThat(runtime.queryForObject("""
                SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """, Boolean.class)).isFalse();
        assertThat(runtime.queryForObject(
                "SELECT has_database_privilege(current_database(), 'CREATE')", Boolean.class))
                .isFalse();
        assertThat(runtime.queryForObject(
                "SELECT has_schema_privilege('public', 'CREATE')", Boolean.class)).isFalse();
        assertThatThrownBy(() -> runtime.execute("SET ROLE booking_migrator"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("SET ROLE booking_admin"))
                .isInstanceOf(DataAccessException.class);
        assertThat(migration.queryForObject("""
                SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """, Boolean.class)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE public.unauthorized_table (id bigint)",
            "ALTER TABLE public.users ADD COLUMN unauthorized_column text",
            "DROP TABLE public.users CASCADE",
            "TRUNCATE public.users CASCADE",
            "CREATE SCHEMA unauthorized_schema",
            "CREATE ROLE unauthorized_role",
            "CREATE DATABASE unauthorized_database",
            "SELECT * FROM liquibase.databasechangelog",
            "DELETE FROM liquibase.databasechangeloglock"
    })
    void runtimeCannotChangeSchemaOrMigrationHistory(String sql) {
        assertThatThrownBy(() -> runtime.execute(sql)).isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                        .isEqualTo("42501"));
    }

    @Test
    void runtimeCanCreateBookingWithCapacityTriggerAndPayment() {
        Long user = runtime.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Role', 'Test', 'hash', 'CUSTOMER') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        Long accommodation = runtime.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('APARTMENT', 'Warsaw', 'Studio', 100, 1) RETURNING id
                """, Long.class);
        Long booking = null;
        try {
            booking = runtime.queryForObject("""
                    INSERT INTO bookings (check_in_date, check_out_date, accommodation_id,
                                          user_id, status)
                    VALUES (CURRENT_DATE + 10, CURRENT_DATE + 12, ?, ?, 'PENDING') RETURNING id
                    """, Long.class, accommodation, user);
            runtime.update("""
                    INSERT INTO payments (status, booking_id, amount_to_pay)
                    VALUES ('PENDING', ?, 200)
                    """, booking);
            runtime.update("UPDATE bookings SET status = 'CONFIRMED' WHERE id = ?", booking);
            assertThat(runtime.queryForObject("SELECT status FROM bookings WHERE id = ?",
                    String.class, booking)).isEqualTo("CONFIRMED");
        } finally {
            if (booking != null) {
                runtime.update("DELETE FROM payments WHERE booking_id = ?", booking);
                runtime.update("DELETE FROM bookings WHERE id = ?", booking);
            }
            runtime.update("DELETE FROM accommodations WHERE id = ?", accommodation);
            runtime.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    @Test
    void futureMigrationTablesAndSequencesReceiveRuntimePrivileges() {
        migration.execute("CREATE TABLE public.role_privilege_probe (id bigserial, value text)");
        try {
            Long id = runtime.queryForObject("""
                    INSERT INTO role_privilege_probe (value) VALUES ('created') RETURNING id
                    """, Long.class);
            runtime.update("UPDATE role_privilege_probe SET value = 'updated' WHERE id = ?", id);
            assertThat(runtime.queryForObject("SELECT value FROM role_privilege_probe WHERE id = ?",
                    String.class, id)).isEqualTo("updated");
            assertThat(runtime.update("DELETE FROM role_privilege_probe WHERE id = ?", id))
                    .isEqualTo(1);
            assertThatThrownBy(() -> runtime.execute("TRUNCATE role_privilege_probe"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            migration.execute("DROP TABLE public.role_privilege_probe");
        }
    }

    @Test
    void existingMigrationHistoryCanBeMovedWithoutReplayingMigrationsOrLosingData()
            throws Exception {
        Integer before = migration.queryForObject(
                "SELECT count(*) FROM liquibase.databasechangelog", Integer.class);
        assertThat(before).isPositive();
        Long user = runtime.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Existing', 'Data', 'hash', 'CUSTOMER') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        try {
            migration.execute("ALTER TABLE liquibase.databasechangelog SET SCHEMA public");
            migration.execute("ALTER TABLE liquibase.databasechangeloglock SET SCHEMA public");
            var provision = POSTGRES.execInContainer("bash", SCRIPT);
            assertThat(provision.getExitCode()).as(provision.getStderr()).isZero();
            migrate();
            assertThat(migration.queryForObject(
                    "SELECT count(*) FROM liquibase.databasechangelog", Integer.class))
                    .isEqualTo(before);
            assertThat(runtime.queryForObject("SELECT first_name FROM users WHERE id = ?",
                    String.class, user)).isEqualTo("Existing");
            assertThatThrownBy(() -> runtime.execute("SELECT * FROM liquibase.databasechangelog"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            runtime.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    private static void migrate() throws Exception {
        DatabaseMigration.migrate(new MockEnvironment()
                .withProperty("LIQUIBASE_URL", POSTGRES.getJdbcUrl())
                .withProperty("LIQUIBASE_USERNAME", "booking_migrator")
                .withProperty("LIQUIBASE_PASSWORD", MIGRATION_PASSWORD));
    }

    private static JdbcTemplate jdbc(String username, String password) {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                username, password));
    }
}

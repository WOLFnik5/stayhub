package com.bookingapp;

import com.bookingapp.testsupport.PostgreSqlLiquibaseIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiquibaseMigrationIntegrationTest extends PostgreSqlLiquibaseIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldKeepPaymentHistoryButRejectTwoPendingAttempts() {
        Long user = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Test', 'Customer', 'test', 'CUSTOMER') RETURNING id
                """, Long.class, java.util.UUID.randomUUID() + "@example.com");
        Long accommodation = jdbcTemplate.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('APARTMENT', 'Warsaw', 'Studio', 100, 1) RETURNING id
                """, Long.class);
        Long booking = jdbcTemplate.queryForObject("""
                INSERT INTO bookings (accommodation_id, user_id, check_in_date, check_out_date, status)
                VALUES (?, ?, '2031-01-01', '2031-01-03', 'PENDING') RETURNING id
                """, Long.class, accommodation, user);
        try {
            String insert = """
                    INSERT INTO payments (booking_id, status, amount_to_pay, session_id, currency)
                    VALUES (?, 'PENDING', 200, ?, 'usd')
                    """;
            jdbcTemplate.update(insert, booking, "migration-first");
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    jdbcTemplate.update(insert, booking, "migration-second"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                    .hasStackTraceContaining("uk_payments_pending_booking");
            jdbcTemplate.update("UPDATE payments SET status = 'EXPIRED' WHERE booking_id = ?", booking);
            jdbcTemplate.update(insert, booking, "migration-second");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE booking_id = ?", Integer.class, booking))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM payments
                    WHERE booking_id = ? AND created_at IS NOT NULL AND currency = 'usd'
                    """, Integer.class, booking)).isEqualTo(2);
        } finally {
            jdbcTemplate.update("DELETE FROM payments WHERE booking_id = ?", booking);
            jdbcTemplate.update("DELETE FROM bookings WHERE id = ?", booking);
            jdbcTemplate.update("DELETE FROM accommodations WHERE id = ?", accommodation);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
    }

    @Test
    void shouldCreateDatabaseChangelogTable() {
        Integer databaseChangelogTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'databasechangelog'
                """,
                Integer.class
        );

        assertThat(databaseChangelogTableCount).isEqualTo(1);
    }

    @Test
    void shouldCreateUsersAndPaymentsTables() {
        Integer usersTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'users'
                """,
                Integer.class
        );
        Integer paymentsTableCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = 'payments'
                """,
                Integer.class
        );

        assertThat(usersTableCount).isEqualTo(1);
        assertThat(paymentsTableCount).isEqualTo(1);
    }
}

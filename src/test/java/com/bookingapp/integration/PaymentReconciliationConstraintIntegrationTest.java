package com.bookingapp.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// A distinct context prevents reuse of a connection pool after the per-class container restarts.
@SpringBootTest(properties = "app.scheduler.payment-reconciliation.enabled=false")
class PaymentReconciliationConstraintIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationShouldPreventPendingAttemptBesideReconciliationRequiredAttempt() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Test', 'User', 'hash', 'CUSTOMER') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        Long accommodationId = jdbc.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('APARTMENT', 'Warsaw', 'Studio', 100, 1) RETURNING id
                """, Long.class);
        Long bookingId = jdbc.queryForObject("""
                INSERT INTO bookings (check_in_date, check_out_date, accommodation_id, user_id, status)
                VALUES (CURRENT_DATE + 10, CURRENT_DATE + 12, ?, ?, 'PENDING') RETURNING id
                """, Long.class, accommodationId, userId);
        try {
            jdbc.update("""
                    INSERT INTO payments (status, booking_id, amount_to_pay)
                    VALUES ('RECONCILIATION_REQUIRED', ?, 200)
                    """, bookingId);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO payments (status, booking_id, amount_to_pay)
                    VALUES ('PENDING', ?, 200)
                    """, bookingId)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_payments_pending_booking");
        } finally {
            jdbc.update("DELETE FROM payments WHERE booking_id = ?", bookingId);
            jdbc.update("DELETE FROM bookings WHERE id = ?", bookingId);
            jdbc.update("DELETE FROM accommodations WHERE id = ?", accommodationId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }
}

package com.bookingapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.testsupport.PostgreSqlLiquibaseIntegrationTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BookingOverlapConstraintIntegrationTest extends PostgreSqlLiquibaseIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private Long accommodationId;
    private Long otherAccommodationId;
    private Long capacityTwoAccommodationId;

    @BeforeEach
    void createFixtures() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (email, first_name, last_name, password, role)
                VALUES (?, 'Test', 'Customer', 'unused-test-hash', 'CUSTOMER') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.com");
        accommodationId = createAccommodation();
        otherAccommodationId = createAccommodation();
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM bookings WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accommodations WHERE id IN (?, ?)",
                accommodationId, otherAccommodationId);
        if (capacityTwoAccommodationId != null) {
            jdbc.update("DELETE FROM accommodations WHERE id = ?", capacityTwoAccommodationId);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void shouldRejectOverlappingInsertWithoutServiceValidation() {
        insertBooking(accommodationId, 10, 15, "PENDING");

        assertThatThrownBy(() -> insertBooking(accommodationId, 12, 17, "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("accommodation_capacity_exceeded");
        assertThat(bookingCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectOverlappingDateUpdateAndPreserveOriginalDates() {
        insertBooking(accommodationId, 10, 15, "CONFIRMED");
        Long bookingId = insertBooking(accommodationId, 20, 25, "PENDING");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE bookings SET check_in_date = ? WHERE id = ?", date(12), bookingId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("accommodation_capacity_exceeded");
        assertThat(jdbc.queryForObject("SELECT check_in_date FROM bookings WHERE id = ?",
                LocalDate.class, bookingId)).isEqualTo(date(20));
    }

    @Test
    void shouldAllowAdjacentDatesInactiveBookingsAndDifferentAccommodations() {
        insertBooking(accommodationId, 10, 15, "PENDING");
        insertBooking(accommodationId, 15, 20, "CONFIRMED");
        insertBooking(accommodationId, 5, 10, "PENDING");
        insertBooking(accommodationId, 12, 17, "CANCELED");
        insertBooking(accommodationId, 12, 17, "EXPIRED");
        insertBooking(otherAccommodationId, 10, 15, "PENDING");

        assertThat(bookingCount()).isEqualTo(6);
    }

    @Test
    void shouldRejectReactivatingOverlappingCanceledBooking() {
        insertBooking(accommodationId, 10, 15, "PENDING");
        Long canceledId = insertBooking(accommodationId, 10, 15, "CANCELED");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE bookings SET status = 'PENDING' WHERE id = ?", canceledId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("accommodation_capacity_exceeded");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?",
                String.class, canceledId)).isEqualTo("CANCELED");
    }

    @Test
    void shouldAllowAReservationCoveringAdjacentStaysWhenCapacityIsTwo() {
        capacityTwoAccommodationId = createAccommodation(2);
        insertBooking(capacityTwoAccommodationId, 10, 12, "PENDING");
        insertBooking(capacityTwoAccommodationId, 12, 14, "PENDING");

        insertBooking(capacityTwoAccommodationId, 10, 14, "PENDING");

        assertThat(bookingCount()).isEqualTo(3);
    }

    private Long createAccommodation() {
        return createAccommodation(1);
    }

    private Long createAccommodation(int availability) {
        return jdbc.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('APARTMENT', 'Krakow', 'Apartment', 180, ?) RETURNING id
                """, Long.class, availability);
    }

    private Long insertBooking(Long accommodation, int start, int end, String status) {
        return jdbc.queryForObject("""
                INSERT INTO bookings
                    (accommodation_id, user_id, check_in_date, check_out_date, status)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, accommodation, userId, date(start), date(end), status);
    }

    private int bookingCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE user_id = ?",
                Integer.class, userId);
    }

    private LocalDate date(int offset) {
        return LocalDate.of(2030, 1, 1).plusDays(offset);
    }
}

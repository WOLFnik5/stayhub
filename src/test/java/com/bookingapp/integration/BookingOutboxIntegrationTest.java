package com.bookingapp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bookingapp.infrastructure.persistence.outbox.OutboxEventEntity;
import com.bookingapp.infrastructure.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.infrastructure.persistence.outbox.OutboxStatus;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.service.BookingService;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.infrastructure.security.AuthenticatedUserPrincipal;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BookingOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccommodationService accommodationService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId;

    @BeforeEach
    void setUpAuthentication() {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    insert into users (email, first_name, last_name, password, role)
                    values (?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, "customer@example.com");
            ps.setString(2, "Test");
            ps.setString(3, "User");
            ps.setString(4, "$2a$10$testpasswordhash");
            ps.setString(5, UserRole.CUSTOMER.name());
            return ps;
        }, keyHolder);

        testUserId = ((Number) keyHolder.getKeys().get("id")).longValue();

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                testUserId,
                "customer@example.com",
                UserRole.CUSTOMER
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities()
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBooking_shouldSaveOutboxEvent() {
        Accommodation accommodation = accommodationService.createAccommodation(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi", "Kitchen"),
                new BigDecimal("120.00"),
                5
        );

        Booking savedBooking = bookingService.createBooking(
                accommodation.getId(),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 15)
        );

        OutboxEventEntity event = outboxEventJpaRepository.findAll().stream()
                .filter(e -> "Booking".equals(e.getAggregateType()))
                .filter(e -> savedBooking.getId().equals(e.getAggregateId()))
                .filter(e -> "BookingCreatedEvent".equals(e.getEventType()))
                .findFirst()
                .orElseThrow();

        assertNotNull(savedBooking.getId());
        assertEquals(testUserId, savedBooking.getUserId());
        assertEquals("Booking", event.getAggregateType());
        assertEquals(savedBooking.getId(), event.getAggregateId());
        assertEquals("BookingCreatedEvent", event.getEventType());
        assertEquals(OutboxStatus.NEW, event.getStatus());
    }
}

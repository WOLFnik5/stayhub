package com.bookingapp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.bookingapp.adapter.out.persistence.outbox.OutboxEventEntity;
import com.bookingapp.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.adapter.out.persistence.outbox.OutboxStatus;
import com.bookingapp.application.model.CreateAccommodationCommand;
import com.bookingapp.application.model.CreateBookingCommand;
import com.bookingapp.application.port.in.accommodation.CreateAccommodationUseCase;
import com.bookingapp.application.port.in.booking.CreateBookingUseCase;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateAccommodationUseCase createAccommodationUseCase;

    @Autowired
    private CreateBookingUseCase createBookingUseCase;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Test
    void createBooking_shouldSaveOutboxEvent() {
        Accommodation accommodation = createAccommodationUseCase.createAccommodation(
                new CreateAccommodationCommand(
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi", "Kitchen"),
                        new BigDecimal("120.00"),
                        5
                )
        );

        LocalDate checkIn = LocalDate.of(2026, 1, 10);
        LocalDate checkOut = LocalDate.of(2026, 1, 15);

        CreateBookingCommand command = new CreateBookingCommand(
                accommodation.getId(),
                checkIn,
                checkOut
        );

        Booking savedBooking = createBookingUseCase.createBooking(command);

        List<OutboxEventEntity> events = outboxEventJpaRepository.findAll();

        assertFalse(events.isEmpty());

        OutboxEventEntity event = events.stream()
                .filter(outboxEvent -> "BookingCreatedEvent".equals(outboxEvent.getEventType()))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertEquals("Booking", event.getAggregateType());
        assertEquals(savedBooking.getId(), event.getAggregateId());
        assertEquals("BookingCreatedEvent", event.getEventType());
        assertEquals(OutboxStatus.NEW, event.getStatus());
    }
}
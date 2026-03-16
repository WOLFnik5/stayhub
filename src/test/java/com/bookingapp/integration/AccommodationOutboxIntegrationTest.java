package com.bookingapp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookingapp.adapter.out.persistence.outbox.OutboxEventEntity;
import com.bookingapp.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.adapter.out.persistence.outbox.OutboxStatus;
import com.bookingapp.application.model.CreateAccommodationCommand;
import com.bookingapp.application.port.in.accommodation.CreateAccommodationUseCase;
import com.bookingapp.domain.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccommodationOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateAccommodationUseCase createAccommodationUseCase;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Test
    void createAccommodation_shouldSaveOutboxEvent() {
        Accommodation savedAccommodation = createAccommodationUseCase.createAccommodation(
                new CreateAccommodationCommand(
                        AccommodationType.APARTMENT,
                        "Kyiv",
                        "55m2",
                        List.of("WiFi", "Kitchen"),
                        new BigDecimal("120.00"),
                        5
                )
        );

        OutboxEventEntity event = outboxEventJpaRepository.findAll().stream()
                .filter(outboxEvent -> "AccommodationCreatedEvent".equals(outboxEvent.getEventType()))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertEquals("Accommodation", event.getAggregateType());
        assertEquals(savedAccommodation.getId(), event.getAggregateId());
        assertEquals("AccommodationCreatedEvent", event.getEventType());
        assertEquals(OutboxStatus.NEW, event.getStatus());
    }
}
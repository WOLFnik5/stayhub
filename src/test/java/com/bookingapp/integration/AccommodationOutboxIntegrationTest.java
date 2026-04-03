package com.bookingapp.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import com.bookingapp.service.AccommodationService;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccommodationOutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AccommodationService accommodationService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Test
    void createAccommodation_shouldSaveOutboxEvent() {
        Accommodation savedAccommodation = accommodationService.createAccommodation(
                AccommodationType.APARTMENT,
                "Kyiv",
                "55m2",
                List.of("WiFi", "Kitchen"),
                new BigDecimal("120.00"),
                5
        );

        assertNotNull(savedAccommodation.getId());

        outboxEventJpaRepository.findAll().forEach(event -> System.out.println(
                "id=" + event.getId()
                        + ", type=" + event.getEventType()
                        + ", aggregateType=" + event.getAggregateType()
                        + ", aggregateId=" + event.getAggregateId()
                        + ", status=" + event.getStatus()
        ));
        List<OutboxEventEntity> events = outboxEventJpaRepository.findAll();

        OutboxEventEntity event = events.stream()
                .filter(outboxEvent -> "AccommodationCreatedEvent".equals(outboxEvent.getEventType()))
                .filter(outboxEvent -> "Accommodation".equals(outboxEvent.getAggregateType()))
                .filter(outboxEvent -> savedAccommodation.getId().equals(outboxEvent.getAggregateId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Outbox event not found. Events: " + events));

        assertEquals("Accommodation", event.getAggregateType());
        assertEquals(savedAccommodation.getId(), event.getAggregateId());
        assertEquals("AccommodationCreatedEvent", event.getEventType());
        assertEquals(OutboxStatus.NEW, event.getStatus());
    }
}

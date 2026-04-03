package com.bookingapp.infrastructure.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.infrastructure.config.KafkaTopicsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OutboxKafkaEventPublisherTest {

    @Test
    void publishAccommodationCreated_shouldSaveOutboxEvent() {
        OutboxEventJpaRepository repository = Mockito.mock(OutboxEventJpaRepository.class);
        KafkaTopicsProperties kafkaTopicsProperties = new KafkaTopicsProperties();
        kafkaTopicsProperties.setAccommodationCreated("accommodation-created");
        kafkaTopicsProperties.setBookingCreated("booking-created");
        kafkaTopicsProperties.setBookingCanceled("booking-canceled");
        kafkaTopicsProperties.setBookingExpired("booking-expired");
        kafkaTopicsProperties.setPaymentSucceeded("payment-succeeded");

        OutboxKafkaEventPublisher publisher = new OutboxKafkaEventPublisher(
                repository,
                kafkaTopicsProperties,
                new ObjectMapper().findAndRegisterModules()
        );

        Accommodation accommodation = new Accommodation(
                5L,
                AccommodationType.APARTMENT,
                "Kyiv, Ukraine",
                "55m2",
                List.of("WiFi", "Kitchen"),
                new BigDecimal("120.00"),
                5
        );

        publisher.publishAccommodationCreated(accommodation);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());

        OutboxEventEntity savedEvent = captor.getValue();

        assertNotNull(savedEvent.getId());
        assertEquals("Accommodation", savedEvent.getAggregateType());
        assertEquals(5L, savedEvent.getAggregateId());
        assertEquals("AccommodationCreatedEvent", savedEvent.getEventType());
        assertEquals("accommodation-created", savedEvent.getTopic());
        assertEquals("5", savedEvent.getEventKey());
        assertEquals(OutboxStatus.NEW, savedEvent.getStatus());
        assertEquals(0, savedEvent.getAttempts());
        assertNotNull(savedEvent.getPayload());
    }
}

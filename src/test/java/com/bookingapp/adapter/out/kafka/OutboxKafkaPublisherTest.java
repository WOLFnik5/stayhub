package com.bookingapp.adapter.out.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.adapter.out.persistence.outbox.OutboxEventEntity;
import com.bookingapp.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.adapter.out.persistence.outbox.OutboxStatus;
import com.bookingapp.infrastructure.config.OutboxProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {
    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishPendingEvents_shouldMarkEventAsSent_whenKafkaSendSucceeds() {
        OutboxProperties outboxProperties = new OutboxProperties(
                5,
                7,
                5000L,
                "0 0 3 * * *"
        );

        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                outboxProperties
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Accommodation",
                10L,
                "AccommodationCreatedEvent",
                "accommodation-created",
                "10",
                "{\"id\":10}"
        );

        when(outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.NEW, OutboxStatus.FAILED)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()))
                .thenReturn(future);

        publisher.publishPendingEvents();

        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsFailed_whenKafkaSendFailsAndAttemptsBelowLimit() {
        OutboxProperties outboxProperties = new OutboxProperties(
                5,
                7,
                5000L,
                "0 0 3 * * *"
        );

        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                outboxProperties
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Booking",
                20L,
                "BookingCreatedEvent",
                "booking-created",
                "20",
                "{\"id\":20}"
        );

        when(outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.NEW, OutboxStatus.FAILED)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));

        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()))
                .thenReturn(failedFuture);

        publisher.publishPendingEvents();

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(1, event.getAttempts());
        assertEquals("Kafka unavailable", event.getLastError());
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsDead_whenKafkaSendFailsAndMaxAttemptsReached() {
        OutboxProperties outboxProperties = new OutboxProperties(
                3,
                7,
                5000L,
                "0 0 3 * * *"
        );

        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                outboxProperties
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Payment",
                30L,
                "PaymentSucceededEvent",
                "payment-succeeded",
                "30",
                "{\"id\":30}"
        );

        event.incrementAttempts();
        event.incrementAttempts();

        when(outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.NEW, OutboxStatus.FAILED)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Permanent serialization error"));

        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()))
                .thenReturn(failedFuture);

        publisher.publishPendingEvents();

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals(3, event.getAttempts());
        assertEquals("Permanent serialization error", event.getLastError());
    }

    @Test
    void cleanupSentEvents_shouldDeleteOldSentEvents() {
        OutboxProperties outboxProperties = new OutboxProperties(
                5,
                7,
                5000L,
                "0 0 3 * * *"
        );

        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                outboxProperties
        );

        publisher.cleanupSentEvents();

        verify(outboxEventJpaRepository).deleteByStatusAndPublishedAtBefore(
                eqStatusSent(),
                any()
        );
    }

    private OutboxStatus eqStatusSent() {
        return OutboxStatus.SENT;
    }
}
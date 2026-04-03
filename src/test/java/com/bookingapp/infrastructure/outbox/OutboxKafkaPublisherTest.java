package com.bookingapp.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import com.bookingapp.infrastructure.config.OutboxProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {

    @Mock
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishPendingEvents_shouldMarkEventAsSent_whenKafkaSendSucceeds() {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                new OutboxProperties(5, 7, 5000L, "0 0 3 * * *")
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Accommodation",
                1L,
                "AccommodationCreatedEvent",
                "accommodation-created",
                "1",
                "{\"id\":1}"
        );

        when(outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.NEW, OutboxStatus.FAILED)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> successFuture =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()))
                .thenReturn(successFuture);

        publisher.publishPendingEvents();

        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNull(event.getLastError());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsFailed_whenKafkaSendFailsAndAttemptsBelowLimit() {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                new OutboxProperties(5, 7, 5000L, "0 0 3 * * *")
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Booking",
                10L,
                "BookingCreatedEvent",
                "booking-created",
                "10",
                "{\"id\":10}"
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
        assertNull(event.getPublishedAt());
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsDead_whenMaxAttemptsReached() {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                new OutboxProperties(3, 7, 5000L, "0 0 3 * * *")
        );

        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Payment",
                20L,
                "PaymentSucceededEvent",
                "payment-succeeded",
                "20",
                "{\"id\":20}"
        );

        event.incrementAttempts();
        event.incrementAttempts();

        when(outboxEventJpaRepository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.NEW, OutboxStatus.FAILED)
        )).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Permanent failure"));

        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()))
                .thenReturn(failedFuture);

        publisher.publishPendingEvents();

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals(3, event.getAttempts());
        assertEquals("Permanent failure", event.getLastError());
    }

    @Test
    void cleanupSentEvents_shouldDeleteOldSentEvents() {
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(
                outboxEventJpaRepository,
                kafkaTemplate,
                new OutboxProperties(5, 7, 5000L, "0 0 3 * * *")
        );

        publisher.cleanupSentEvents();

        verify(outboxEventJpaRepository).deleteByStatusAndPublishedAtBefore(
                eq(OutboxStatus.SENT),
                any()
        );
    }
}

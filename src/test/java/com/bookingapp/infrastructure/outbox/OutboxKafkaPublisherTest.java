package com.bookingapp.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookingapp.infrastructure.config.OutboxProperties;
import com.bookingapp.persistence.outbox.OutboxEventEntity;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxKafkaPublisherTest {

    @Mock
    private OutboxTransactionService outboxTransactionService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(
                5,
                7,
                5000L,
                "0 0 3 * * *",
                100,
                10,
                60000L
        );

        publisher = new OutboxKafkaPublisher(
                outboxTransactionService,
                kafkaTemplate,
                properties
        );
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsSent_whenKafkaSendSucceeds() {
        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Accommodation",
                1L,
                "AccommodationCreatedEvent",
                "accommodation-created",
                "1",
                "{\"id\":1}"
        );

        event.markProcessing();

        when(outboxTransactionService.claimBatch(100))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> successFuture =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                any(ProducerRecord.class)
        )).thenReturn(successFuture);

        publisher.publishPendingEvents();

        verify(outboxTransactionService)
                .markSent(event.getId(), event.getClaimToken());

        verify(
                outboxTransactionService,
                never()
        ).markFailed(
                any(),
                any(),
                any(),
                any(Integer.class)
        );
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsFailed_whenKafkaSendFails() {
        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Booking",
                10L,
                "BookingCreatedEvent",
                "booking-created",
                "10",
                "{\"id\":10}"
        );

        event.markProcessing();

        when(outboxTransactionService.claimBatch(100))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                any(ProducerRecord.class)
        )).thenReturn(failedFuture);

        publisher.publishPendingEvents();

        verify(outboxTransactionService).markFailed(
                eq(event.getId()),
                eq(event.getClaimToken()),
                eq("Kafka unavailable"),
                eq(5)
        );

        verify(
                outboxTransactionService,
                never()
        ).markSent(event.getId(), event.getClaimToken());
    }

    @Test
    void publishPendingEvents_shouldPutEventIdIntoKafkaHeader() {
        OutboxEventEntity event = OutboxEventEntity.newEvent(
                "Booking",
                10L,
                "BookingCreatedEvent",
                "booking-created",
                "10",
                "{\"id\":10}"
        );

        event.markProcessing();

        when(outboxTransactionService.claimBatch(100))
                .thenReturn(List.of(event));

        when(kafkaTemplate.send(
                any(ProducerRecord.class)
        )).thenReturn(
                CompletableFuture.completedFuture(null)
        );

        publisher.publishPendingEvents();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> record =
                captor.getValue();

        assertThat(record.topic())
                .isEqualTo("booking-created");

        assertThat(record.key())
                .isEqualTo("10");

        assertThat(record.value())
                .isEqualTo("{\"id\":10}");

        assertThat(record.headers()
                .lastHeader(
                        OutboxKafkaPublisher.EVENT_ID_HEADER
                ))
                .isNotNull();

        String eventIdHeader = new String(
                record.headers()
                        .lastHeader(
                                OutboxKafkaPublisher.EVENT_ID_HEADER
                        )
                        .value(),
                StandardCharsets.UTF_8
        );

        assertThat(eventIdHeader)
                .isEqualTo(event.getId().toString());
    }

    @Test
    void recoverStaleClaims_shouldDelegateToTransactionService() {
        publisher.recoverStaleClaims();

        verify(outboxTransactionService)
                .recoverStaleClaims(any());
    }

    @Test
    void cleanupSentEvents_shouldDelegateToTransactionService() {
        publisher.cleanupSentEvents();

        verify(outboxTransactionService)
                .cleanupSentEvents(any());
    }
}

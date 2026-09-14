package com.bookingapp.infrastructure.outbox;

import com.bookingapp.infrastructure.config.OutboxProperties;
import com.bookingapp.persistence.outbox.OutboxEventEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxKafkaPublisher {

    public static final String EVENT_ID_HEADER = "eventId";

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    private final OutboxTransactionService outboxTransactionService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public OutboxKafkaPublisher(
            OutboxTransactionService outboxTransactionService,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties outboxProperties
    ) {
        this.outboxTransactionService = outboxTransactionService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.outbox.publish-fixed-delay-ms:5000}"
    )
    public void publishPendingEvents() {
        List<OutboxEventEntity> events =
                outboxTransactionService.claimBatch(
                        outboxProperties.batchSize()
                );

        for (OutboxEventEntity event : events) {
            publishSingleEvent(event);
        }
    }

    @Scheduled(
            fixedDelayString =
                    "${app.outbox.recovery-fixed-delay-ms:60000}"
    )
    public void recoverStaleClaims() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(
                        outboxProperties.claimTimeoutMinutes()
                );

        outboxTransactionService.recoverStaleClaims(threshold);
    }

    @Scheduled(
            cron = "${app.outbox.cleanup-cron:0 0 3 * * *}"
    )
    public void cleanupSentEvents() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusDays(
                        outboxProperties.sentRetentionDays()
                );

        outboxTransactionService.cleanupSentEvents(threshold);
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        try {
            sendToKafka(event);

            outboxTransactionService.markSent(event.getId(), event.getClaimToken());
        } catch (Exception exception) {
            Throwable cause = unwrap(exception);

            outboxTransactionService.markFailed(
                    event.getId(),
                    event.getClaimToken(),
                    truncate(
                            cause.getMessage(),
                            ERROR_MESSAGE_MAX_LENGTH
                    ),
                    outboxProperties.maxAttempts()
            );
        }
    }

    private void sendToKafka(OutboxEventEntity event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        event.getTopic(),
                        event.getEventKey(),
                        event.getPayload()
                );

        record.headers().add(
                EVENT_ID_HEADER,
                event.getId()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        kafkaTemplate.send(record).join();
    }

    private Throwable unwrap(Exception exception) {
        if (exception
                instanceof java.util.concurrent.CompletionException
                && exception.getCause() != null) {
            return exception.getCause();
        }

        return exception;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}

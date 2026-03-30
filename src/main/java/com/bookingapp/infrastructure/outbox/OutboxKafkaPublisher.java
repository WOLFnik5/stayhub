package com.bookingapp.infrastructure.outbox;

import com.bookingapp.infrastructure.config.OutboxProperties;
import com.bookingapp.infrastructure.persistence.outbox.OutboxEventEntity;
import com.bookingapp.infrastructure.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.infrastructure.persistence.outbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxKafkaPublisher {
    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public OutboxKafkaPublisher(
            OutboxEventJpaRepository outboxEventJpaRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxProperties outboxProperties
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxEventJpaRepository
                .findTop100ByStatusInOrderByCreatedAtAsc(List.of(
                        OutboxStatus.NEW,
                        OutboxStatus.FAILED
                ));

        for (OutboxEventEntity event : events) {
            publishSingleEvent(event);
        }
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupSentEvents() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusDays(outboxProperties.sentRetentionDays());
        outboxEventJpaRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.SENT, threshold);
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).join();
            event.markSent();
        } catch (Exception exception) {
            event.incrementAttempts();

            Throwable cause = exception;
            if (exception instanceof java.util.concurrent.CompletionException
                    && exception.getCause() != null) {
                cause = exception.getCause();
            }
            String errorMessage = truncate(cause.getMessage(), ERROR_MESSAGE_MAX_LENGTH);

            if (event.getAttempts() >= outboxProperties.maxAttempts()) {
                event.markDead(errorMessage);
            } else {
                event.markFailed(errorMessage);
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

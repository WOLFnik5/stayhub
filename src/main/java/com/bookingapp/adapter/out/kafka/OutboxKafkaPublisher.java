package com.bookingapp.adapter.out.kafka;

import com.bookingapp.adapter.out.persistence.outbox.OutboxEventEntity;
import com.bookingapp.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.adapter.out.persistence.outbox.OutboxStatus;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxKafkaPublisher {
    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxKafkaPublisher(
            OutboxEventJpaRepository outboxEventJpaRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxEventJpaRepository
                .findTop100ByStatusInOrderByCreatedAtAsc(List.of(OutboxStatus.NEW, OutboxStatus.FAILED));

        for (OutboxEventEntity event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).join();
                event.markSent();
            } catch (Exception exception) {
                event.markFailed(truncate(exception.getMessage(), 2000));
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
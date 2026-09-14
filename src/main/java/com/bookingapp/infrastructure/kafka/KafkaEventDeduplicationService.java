package com.bookingapp.infrastructure.kafka;

import com.bookingapp.persistence.inbox.ProcessedEventId;
import com.bookingapp.persistence.inbox.ProcessedEventJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KafkaEventDeduplicationService {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    public KafkaEventDeduplicationService(
            ProcessedEventJpaRepository processedEventJpaRepository
    ) {
        this.processedEventJpaRepository =
                processedEventJpaRepository;
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(
            UUID eventId,
            String consumer
    ) {
        return processedEventJpaRepository.existsById(new ProcessedEventId(eventId, consumer));
    }

    @Transactional
    public void markProcessed(UUID eventId, String consumer) {
        processedEventJpaRepository.tryInsert(eventId, consumer);
    }
}

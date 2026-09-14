package com.bookingapp.infrastructure.outbox;

import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxTransactionService {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public OutboxTransactionService(
            OutboxEventJpaRepository outboxEventJpaRepository
    ) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
    }

    @Transactional
    public List<OutboxEventEntity> claimBatch(int batchSize) {
        List<OutboxEventEntity> events =
                outboxEventJpaRepository.findBatchForProcessing(batchSize);

        events.forEach(OutboxEventEntity::markProcessing);

        return List.copyOf(events);
    }

    @Transactional
    public boolean markSent(UUID eventId, UUID claimToken) {
        return outboxEventJpaRepository.markSentIfClaimOwned(
                eventId,
                claimToken,
                OutboxStatus.PROCESSING,
                OutboxStatus.SENT
        ) == 1;
    }

    @Transactional
    public boolean markFailed(
            UUID eventId,
            UUID claimToken,
            String errorMessage,
            int maxAttempts
    ) {
        return outboxEventJpaRepository.markFailedIfClaimOwned(
                eventId,
                claimToken,
                errorMessage,
                maxAttempts,
                OutboxStatus.PROCESSING,
                OutboxStatus.FAILED,
                OutboxStatus.DEAD
        ) == 1;
    }

    @Transactional
    public int recoverStaleClaims(LocalDateTime threshold) {
        return outboxEventJpaRepository.releaseStaleClaims(
                OutboxStatus.PROCESSING,
                OutboxStatus.FAILED,
                threshold,
                "Outbox processing lease expired"
        );
    }

    @Transactional
    public long cleanupSentEvents(LocalDateTime threshold) {
        return outboxEventJpaRepository
                .deleteByStatusAndPublishedAtBefore(
                        OutboxStatus.SENT,
                        threshold
                );
    }

}

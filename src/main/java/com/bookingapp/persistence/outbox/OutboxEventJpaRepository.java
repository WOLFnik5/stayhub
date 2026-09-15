package com.bookingapp.persistence.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository
        extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("""
            SELECT e.status AS status, COUNT(e) AS total, MIN(e.createdAt) AS oldest
            FROM OutboxEventEntity e GROUP BY e.status
            """)
    List<StatusSummary> summarizeStatuses();

    interface StatusSummary {
        OutboxStatus getStatus();

        long getTotal();

        LocalDateTime getOldest();
    }

    @Query(
            value = """
                    SELECT *
                    FROM outbox_events
                    WHERE status IN ('NEW', 'FAILED')
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :batchSize
                    """,
            nativeQuery = true
    )
    List<OutboxEventEntity> findBatchForProcessing(
            @Param("batchSize") int batchSize
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.status = :failedStatus,
                e.claimedAt = null,
                e.claimToken = null,
                e.lastError = :errorMessage
            WHERE e.status = :processingStatus
              AND e.claimedAt < :threshold
            """)
    int releaseStaleClaims(
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("failedStatus") OutboxStatus failedStatus,
            @Param("threshold") LocalDateTime threshold,
            @Param("errorMessage") String errorMessage
    );

    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.status = :sentStatus,
                e.publishedAt = CURRENT_TIMESTAMP,
                e.claimedAt = null,
                e.claimToken = null,
                e.lastError = null
            WHERE e.id = :eventId
              AND e.status = :processingStatus
              AND e.claimToken = :claimToken
            """)
    int markSentIfClaimOwned(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("sentStatus") OutboxStatus sentStatus
    );

    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.status = CASE
                    WHEN e.attempts + 1 >= :maxAttempts THEN :deadStatus
                    ELSE :failedStatus
                END,
                e.attempts = e.attempts + 1,
                e.claimedAt = null,
                e.claimToken = null,
                e.lastError = :errorMessage
            WHERE e.id = :eventId
              AND e.status = :processingStatus
              AND e.claimToken = :claimToken
            """)
    int markFailedIfClaimOwned(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("errorMessage") String errorMessage,
            @Param("maxAttempts") int maxAttempts,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("failedStatus") OutboxStatus failedStatus,
            @Param("deadStatus") OutboxStatus deadStatus
    );

    long deleteByStatusAndPublishedAtBefore(
            OutboxStatus status,
            LocalDateTime publishedAtBefore
    );
}

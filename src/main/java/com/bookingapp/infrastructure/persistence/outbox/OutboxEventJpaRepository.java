package com.bookingapp.infrastructure.persistence.outbox;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop100ByStatusInOrderByCreatedAtAsc(
            Collection<OutboxStatus> statuses);

    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, LocalDateTime publishedAtBefore);
}

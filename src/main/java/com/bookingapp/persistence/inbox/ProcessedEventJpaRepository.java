package com.bookingapp.persistence.inbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO processed_events (
                        event_id,
                        consumer,
                        processed_at
                    )
                    VALUES (
                        :eventId,
                        :consumer,
                        CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (event_id, consumer)
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int tryInsert(
            @Param("eventId") UUID eventId,
            @Param("consumer") String consumer
    );
}

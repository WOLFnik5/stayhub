package com.bookingapp.persistence.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
@Getter
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer", nullable = false, length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(
            UUID eventId,
            String consumer,
            LocalDateTime processedAt
    ) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public static ProcessedEventEntity processed(
            UUID eventId,
            String consumer
    ) {
        return new ProcessedEventEntity(
                eventId,
                consumer,
                LocalDateTime.now()
        );
    }
}

package com.bookingapp.persistence.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(
            UUID id,
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            String eventKey,
            String payload,
            OutboxStatus status,
            int attempts,
            LocalDateTime createdAt,
            LocalDateTime publishedAt,
            LocalDateTime claimedAt,
            UUID claimToken,
            String lastError
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.claimedAt = claimedAt;
        this.claimToken = claimToken;
        this.lastError = lastError;
    }

    public static OutboxEventEntity newEvent(
            String aggregateType,
            Long aggregateId,
            String eventType,
            String topic,
            String eventKey,
            String payload
    ) {
        return new OutboxEventEntity(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                topic,
                eventKey,
                payload,
                OutboxStatus.NEW,
                0,
                LocalDateTime.now(),
                null,
                null,
                null,
                null
        );
    }

    public void markProcessing() {
        status = OutboxStatus.PROCESSING;
        claimedAt = LocalDateTime.now();
        claimToken = UUID.randomUUID();
        lastError = null;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public void markSent() {
        status = OutboxStatus.SENT;
        publishedAt = LocalDateTime.now();
        claimedAt = null;
        claimToken = null;
        lastError = null;
    }

    public void markFailed(String errorMessage) {
        status = OutboxStatus.FAILED;
        claimedAt = null;
        claimToken = null;
        lastError = errorMessage;
    }

    public void markDead(String errorMessage) {
        status = OutboxStatus.DEAD;
        claimedAt = null;
        claimToken = null;
        lastError = errorMessage;
    }
}

package com.bookingapp.infrastructure.observability;

import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
    private final OutboxEventJpaRepository repository;
    private volatile Map<OutboxStatus, Long> counts = Map.of();
    private volatile LocalDateTime oldest;
    private volatile long refreshedAt;

    public OutboxMetrics(OutboxEventJpaRepository repository, MeterRegistry registry) {
        this.repository = repository;
        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder("booking.outbox.events", this,
                    metrics -> metrics.counts.getOrDefault(status, 0L))
                    .tag("status", status.name()).register(registry);
        }
        Gauge.builder("booking.outbox.oldest.age.seconds", this, OutboxMetrics::oldestAge)
                .register(registry);
        Gauge.builder("booking.outbox.snapshot.timestamp", this, metrics -> metrics.refreshedAt)
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-delay-ms:30000}")
    public void refresh() {
        Map<OutboxStatus, Long> next = new EnumMap<>(OutboxStatus.class);
        LocalDateTime earliest = null;
        for (var summary : repository.summarizeStatuses()) {
            next.put(summary.getStatus(), summary.getTotal());
            if (summary.getStatus() != OutboxStatus.SENT
                    && summary.getStatus() != OutboxStatus.DEAD
                    && (earliest == null || summary.getOldest().isBefore(earliest))) {
                earliest = summary.getOldest();
            }
        }
        oldest = earliest;
        counts = Map.copyOf(next);
        refreshedAt = System.currentTimeMillis() / 1000;
    }

    private double oldestAge() {
        LocalDateTime value = oldest;
        return value == null ? 0
                : Math.max(0, Duration.between(value, LocalDateTime.now()).toSeconds());
    }
}

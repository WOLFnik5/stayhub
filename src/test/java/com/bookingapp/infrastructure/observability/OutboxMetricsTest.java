package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {
    @Test
    void includesOnlyPendingStatusesInAgeAndClearsRemovedCounts() {
        OutboxEventJpaRepository repository = mock(OutboxEventJpaRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMetrics metrics = new OutboxMetrics(repository, registry);
        when(repository.summarizeStatuses()).thenReturn(List.of(
                summary(OutboxStatus.NEW, 2, LocalDateTime.now().minusSeconds(90)),
                summary(OutboxStatus.DEAD, 4, LocalDateTime.now().minusDays(2)),
                summary(OutboxStatus.SENT, 8, LocalDateTime.now().minusDays(3))));
        metrics.refresh();
        assertThat(registry.get("booking.outbox.events").tag("status", "DEAD").gauge().value()).isEqualTo(4);
        assertThat(registry.get("booking.outbox.oldest.age.seconds").gauge().value()).isBetween(89.0, 95.0);
        assertThat(registry.get("booking.outbox.snapshot.timestamp").gauge().value()).isPositive();
        when(repository.summarizeStatuses()).thenReturn(List.of());
        metrics.refresh();
        assertThat(registry.get("booking.outbox.events").tag("status", "DEAD").gauge().value()).isZero();
        assertThat(registry.get("booking.outbox.oldest.age.seconds").gauge().value()).isZero();
    }

    private OutboxEventJpaRepository.StatusSummary summary(OutboxStatus status, long count,
            LocalDateTime oldest) {
        return new OutboxEventJpaRepository.StatusSummary() {
            public OutboxStatus getStatus() { return status; }
            public long getTotal() { return count; }
            public LocalDateTime getOldest() { return oldest; }
        };
    }
}

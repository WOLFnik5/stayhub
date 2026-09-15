package com.bookingapp.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlowTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowTelemetry.class);
    private final MeterRegistry registry;

    public FlowTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String stage, String outcome, long started, String errorType) {
        long elapsed = System.nanoTime() - started;
        registry.timer("booking.flow.duration", "stage", stage, "outcome", outcome)
                .record(elapsed, TimeUnit.NANOSECONDS);
        var builder = errorType == null ? LOGGER.atInfo() : LOGGER.atWarn();
        builder.addKeyValue("stage", stage).addKeyValue("outcome", outcome)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(elapsed))
                .addKeyValue("errorType", errorType).log("Async flow transition");
    }

    public void count(String stage, String outcome, int count) {
        if (count > 0) {
            registry.counter("booking.flow.events", "stage", stage, "outcome", outcome)
                    .increment(count);
            LOGGER.atInfo().addKeyValue("stage", stage).addKeyValue("outcome", outcome)
                    .addKeyValue("count", count).log("Async flow event");
        }
    }
}

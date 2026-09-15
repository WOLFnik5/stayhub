package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = StructuredLoggingTest.LoggingOnly.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ReadableLoggingTest {
    @Test
    void textContainsCorrelationAndTransitionFields(CapturedOutput output) {
        try (var scope = CorrelationContext.open("text-request", "text-event")) {
            new FlowTelemetry(new SimpleMeterRegistry())
                    .record("telegram", "accepted", System.nanoTime(), null);
        }
        String line = output.getOut().lines()
                .filter(value -> value.contains("text-request"))
                .findFirst().orElseThrow();
        assertThat(line).contains("eventId=text-event", "stage=\"telegram\"",
                "outcome=\"accepted\"", "durationMs=");
    }
}

package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = StructuredLoggingTest.LoggingOnly.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("observability")
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {
    @Test
    void jsonContainsCorrelationAndTransitionFields(CapturedOutput output) throws Exception {
        try (var scope = CorrelationContext.open("json-request", "json-event")) {
            new FlowTelemetry(new SimpleMeterRegistry())
                    .record("telegram", "failed", System.nanoTime(), "HTTP_401");
        }
        String line = output.getOut().lines()
                .filter(value -> value.contains("json-request"))
                .findFirst().orElseThrow();
        var json = new ObjectMapper().readTree(line);
        assertThat(json.get("correlationId").asText()).isEqualTo("json-request");
        assertThat(json.get("eventId").asText()).isEqualTo("json-event");
        assertThat(json.get("stage").asText()).isEqualTo("telegram");
        assertThat(json.get("outcome").asText()).isEqualTo("failed");
        assertThat(json.get("errorType").asText()).isEqualTo("HTTP_401");
        assertThat(json.has("durationMs")).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class LoggingOnly {
    }
}

package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = StructuredLoggingTest.LoggingOnly.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ReadableLoggingTest {

    @Test
    void textContainsCorrelationAndTransitionFields(CapturedOutput output)
            throws Exception {

        try (var scope = CorrelationContext.open(
                "text-request",
                "text-event"
        )) {
            new FlowTelemetry(new SimpleMeterRegistry())
                    .record(
                            "telegram",
                            "accepted",
                            System.nanoTime(),
                            null
                    );
        }

        String line = output.getOut()
                .lines()
                .filter(value -> value.contains("text-request"))
                .findFirst()
                .orElseThrow();

        var json = new ObjectMapper().readTree(line);

        assertThat(json.get("correlationId").asText())
                .isEqualTo("text-request");

        assertThat(json.get("eventId").asText())
                .isEqualTo("text-event");

        assertThat(json.get("stage").asText())
                .isEqualTo("telegram");

        assertThat(json.get("outcome").asText())
                .isEqualTo("accepted");

        assertThat(json.has("durationMs"))
                .isTrue();
    }
}
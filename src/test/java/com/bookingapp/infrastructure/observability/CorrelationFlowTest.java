package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationFlowTest {
    @AfterEach
    void clearContext() {
        MDC.clear();
    }

    @Test
    void httpReturnsCorrelationAndRestoresContextOnFailure() {
        MDC.put("unrelated", "previous");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationContext.HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new CorrelationFilter().doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(CorrelationContext.KEY)).isEqualTo("request-123");
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(response.getHeader(CorrelationContext.HEADER)).isEqualTo("request-123");
        assertThat(MDC.get(CorrelationContext.KEY)).isNull();
        assertThat(MDC.get("unrelated")).isEqualTo("previous");
    }

    @Test
    void invalidOrOversizedIdsAreReplaced() {
        assertThat(CorrelationContext.normalize("injected\nfield=value"))
                .matches("[a-f0-9-]{36}");
        assertThat(CorrelationContext.normalize("x".repeat(65))).hasSize(36);
        assertThat(CorrelationContext.normalize(null)).hasSize(36);
    }

    @Test
    void kafkaRestoresPersistedCorrelationAndCleansUpAfterFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelegramRecordInterceptor interceptor = new TelegramRecordInterceptor(new FlowTelemetry(registry), new com.bookingapp.infrastructure.observability.FlowTracing(io.opentelemetry.api.OpenTelemetry.noop()));
        ConsumerRecord<String, String> record = new ConsumerRecord<>("booking-created", 2, 19, "1", "{}");
        record.headers().add("eventId", "event-123".getBytes(StandardCharsets.UTF_8));
        record.headers().add(CorrelationContext.HEADER, "request-123".getBytes(StandardCharsets.UTF_8));
        interceptor.intercept(record, null);
        assertThat(MDC.get(CorrelationContext.KEY)).isEqualTo("request-123");
        assertThat(MDC.get("eventId")).isEqualTo("event-123");
        assertThat(MDC.get("offset")).isEqualTo("19");
        interceptor.failure(record, new IllegalStateException("secret"), null);
        interceptor.afterRecord(record, null);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertThat(registry.get("booking.flow.duration").tags("stage", "consumer", "outcome", "failed")
                .timer().count()).isEqualTo(1);
        ConsumerRecord<String, String> legacy = new ConsumerRecord<>("booking-created", 0, 20, "2", "{}");
        legacy.headers().add("eventId", "legacy-event".getBytes(StandardCharsets.UTF_8));
        interceptor.intercept(legacy, null);
        assertThat(MDC.get(CorrelationContext.KEY)).isEqualTo("legacy-event");
        interceptor.success(legacy, null);
        interceptor.afterRecord(legacy, null);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}

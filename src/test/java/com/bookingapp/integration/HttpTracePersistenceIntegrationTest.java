package com.bookingapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.infrastructure.kafka.OutboxKafkaEventPublisher;
import com.bookingapp.infrastructure.outbox.OutboxKafkaPublisher;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import({HttpTracePersistenceIntegrationTest.TracingConfiguration.class,
        HttpTracePersistenceIntegrationTest.TraceEndpoint.class})
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
class HttpTracePersistenceIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InMemorySpanExporter exporter;
    @Autowired
    private SdkTracerProvider provider;
    @MockitoBean
    private OutboxKafkaPublisher scheduledPublisher;

    @Test
    void httpObservationAndDurableOutboxShareIncomingW3cTrace() throws Exception {
        mvc.perform(post("/auth/tracing-test")
                        .header("traceparent", "00-11111111111111111111111111111111-2222222222222222-01")
                        .header("X-Correlation-ID", "http-durable-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("11111111111111111111111111111111"));
        provider.forceFlush().join(5, TimeUnit.SECONDS);
        var spans = exporter.getFinishedSpanItems();
        var server = spans.stream().filter(span -> span.getKind() == SpanKind.SERVER).findFirst().orElseThrow();
        var enqueue = spans.stream().filter(span -> span.getName().equals("outbox.enqueue")).findFirst().orElseThrow();
        assertThat(server.getParentSpanId()).isEqualTo("2222222222222222");
        assertThat(enqueue.getTraceId()).isEqualTo(server.getTraceId());
        // Spring Security contributes intermediate observations between HTTP and the controller.
        String ancestor = enqueue.getParentSpanId();
        while (!ancestor.equals(server.getSpanId())) {
            String id = ancestor;
            ancestor = spans.stream().filter(span -> span.getSpanId().equals(id))
                    .findFirst().orElseThrow().getParentSpanId();
        }
        assertThat(jdbc.queryForObject("SELECT trace_parent FROM outbox_events WHERE correlation_id = ?",
                String.class, "http-durable-trace"))
                .isEqualTo("00-" + server.getTraceId() + "-" + enqueue.getSpanId() + "-01");
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TracingConfiguration {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    @RestController
    @TestComponent
    static class TraceEndpoint {
        private final OutboxKafkaEventPublisher publisher;

        TraceEndpoint(OutboxKafkaEventPublisher publisher) {
            this.publisher = publisher;
        }

        @PostMapping("/auth/tracing-test")
        Map<String, String> createEvent() {
            publisher.publishAccommodationCreated(new Accommodation(123L,
                    AccommodationType.APARTMENT, "test", "test", List.of(), BigDecimal.TEN, 1));
            return Map.of("traceId", Span.current().getSpanContext().getTraceId());
        }
    }
}

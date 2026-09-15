package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.enums.AccommodationType;
import com.bookingapp.infrastructure.config.KafkaTopicsProperties;
import com.bookingapp.infrastructure.config.OutboxProperties;
import com.bookingapp.infrastructure.config.TelegramProperties;
import com.bookingapp.infrastructure.kafka.OutboxKafkaEventPublisher;
import com.bookingapp.infrastructure.outbox.OutboxKafkaPublisher;
import com.bookingapp.infrastructure.outbox.OutboxTransactionService;
import com.bookingapp.infrastructure.telegram.TelegramBotClient;
import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FlowTracingTest {
    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
    private final FlowTracing tracing = new FlowTracing(sdk);
    private final FlowTelemetry telemetry = new FlowTelemetry(new SimpleMeterRegistry());

    @AfterEach
    void close() {
        sdk.close();
        MDC.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistedParentContinuesThroughPublicationAndConsumerRetries() throws Exception {
        OutboxEventJpaRepository repository = mock(OutboxEventJpaRepository.class);
        AtomicReference<OutboxEventEntity> saved = new AtomicReference<>();
        when(repository.save(any())).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get();
        });
        KafkaTopicsProperties topics = new KafkaTopicsProperties();
        topics.setAccommodationCreated("accommodation-created");
        OutboxKafkaEventPublisher enqueue = new OutboxKafkaEventPublisher(repository, topics,
                new ObjectMapper().findAndRegisterModules(), tracing);
        try (var correlation = CorrelationContext.open("request-123", null);
                var http = tracing.start("http.request", SpanKind.SERVER)) {
            enqueue.publishAccommodationCreated(new Accommodation(1L, AccommodationType.APARTMENT,
                    "test", "test", List.of(), BigDecimal.TEN, 1));
        }
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
        assertThat(saved.get().getTraceParent()).hasSize(55);

        // Reconstruct the event as a different worker would after reading the durable row.
        OutboxEventEntity restored = OutboxEventEntity.newEvent("Accommodation", 1L,
                "AccommodationCreatedEvent", "accommodation-created", "1", saved.get().getPayload());
        restored.setId(saved.get().getId());
        restored.setCorrelationId(saved.get().getCorrelationId());
        restored.setTraceParent(saved.get().getTraceParent());
        restored.setTraceState(saved.get().getTraceState());
        restored.markProcessing();
        OutboxTransactionService transactions = mock(OutboxTransactionService.class);
        when(transactions.claimBatch(100)).thenReturn(List.of(restored));
        when(transactions.markSent(any(), any())).thenReturn(true);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        AtomicReference<ProducerRecord<String, String>> sent = new AtomicReference<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> {
            sent.set(call.getArgument(0));
            return CompletableFuture.completedFuture(null);
        });
        OutboxKafkaPublisher publisher = new OutboxKafkaPublisher(transactions, kafka,
                new OutboxProperties(5, 7, 5000L, "0 0 3 * * *", 100, 10, 60000L),
                telemetry, new FlowTracing(sdk));
        publisher.publishPendingEvents();
        ProducerRecord<String, String> wire = sent.get();
        ConsumerRecord<String, String> record = new ConsumerRecord<>(wire.topic(), 0, 1L,
                wire.key(), wire.value());
        wire.headers().forEach(header -> record.headers().add(header));

        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/botsecret-token/sendMessage"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("secret-token private"));
        server.expect(requestTo("https://telegram.test/botsecret-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));
        TelegramProperties properties = new TelegramProperties();
        properties.setBotToken("secret-token");
        properties.setChatId("private-chat");
        TelegramBotClient telegram = new TelegramBotClient(builder.build(), properties, telemetry, tracing);
        TelegramRecordInterceptor interceptor = new TelegramRecordInterceptor(telemetry, tracing);
        interceptor.intercept(record, null);
        assertThatThrownBy(() -> telegram.sendMessage("private payload"))
                .hasMessage("Telegram request failed: HTTP_503");
        interceptor.failure(record, new IllegalStateException("secret-token"), null);
        interceptor.afterRecord(record, null);
        interceptor.intercept(record, null);
        telegram.sendMessage("private payload");
        interceptor.success(record, null);
        interceptor.afterRecord(record, null);
        server.verify();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        SpanData http = span("http.request");
        SpanData persisted = span("outbox.enqueue");
        SpanData published = span("outbox.publish");
        assertThat(spans).hasSize(7).allMatch(value -> value.getTraceId().equals(http.getTraceId()));
        assertThat(persisted.getParentSpanId()).isEqualTo(http.getSpanId());
        assertThat(published.getParentSpanId()).isEqualTo(persisted.getSpanId());
        List<SpanData> consumed = spans.stream().filter(value -> value.getName().equals("telegram.consume")).toList();
        assertThat(consumed).hasSize(2).allMatch(value -> value.getParentSpanId().equals(published.getSpanId()));
        assertThat(consumed.get(0).getSpanId()).isNotEqualTo(consumed.get(1).getSpanId());
        assertThat(consumed.get(0).getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(spans.stream().filter(value -> value.getName().equals("telegram.send")))
                .allMatch(value -> consumed.stream().anyMatch(parent -> parent.getSpanId().equals(value.getParentSpanId())));
        assertThat(spans.toString()).doesNotContain("secret-token", "private-chat", "private payload");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }

    @Test
    void missingOrInvalidStoredContextStartsNewRootInsteadOfJoiningPoller() {
        try (var poller = tracing.start("poller", SpanKind.INTERNAL)) {
            String parent = Span.current().getSpanContext().getTraceId();
            try (var legacy = tracing.resume("legacy", SpanKind.PRODUCER, null, null)) {
                assertThat(Span.current().getSpanContext().getTraceId()).isNotEqualTo(parent);
            }
            try (var invalid = tracing.resume("invalid", SpanKind.PRODUCER, "bad", "bad")) {
                assertThat(Span.current().getSpanContext().getTraceId()).isNotEqualTo(parent);
            }
            assertThat(Span.current().getSpanContext().getTraceId()).isEqualTo(parent);
        }
        assertThat(span("legacy").getParentSpanId()).isEqualTo("0000000000000000");
        assertThat(span("invalid").getParentSpanId()).isEqualTo("0000000000000000");
    }

    @Test
    void carriesW3cSamplingDecisionAndTracestateWithoutBaggage() {
        String parent = "00-11111111111111111111111111111111-2222222222222222-00";
        try (var scope = tracing.resume("unsampled", SpanKind.PRODUCER, parent, "vendor=value")) {
            assertThat(tracing.headers().get("traceparent")).startsWith("00-11111111111111111111111111111111-")
                    .endsWith("-00");
            assertThat(tracing.headers()).containsEntry("tracestate", "vendor=value");
            assertThat(tracing.headers()).doesNotContainKey("baggage");
        }
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    private SpanData span(String name) {
        return exporter.getFinishedSpanItems().stream().filter(value -> value.getName().equals(name))
                .findFirst().orElseThrow();
    }
}

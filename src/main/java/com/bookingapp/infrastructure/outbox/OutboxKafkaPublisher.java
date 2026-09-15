package com.bookingapp.infrastructure.outbox;

import com.bookingapp.infrastructure.config.OutboxProperties;
import com.bookingapp.infrastructure.observability.CorrelationContext;
import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.observability.SafeFailure;
import com.bookingapp.persistence.outbox.OutboxEventEntity;
import io.opentelemetry.api.trace.SpanKind;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxKafkaPublisher {

    public static final String EVENT_ID_HEADER = "eventId";

    private final FlowTelemetry telemetry;
    private final FlowTracing tracing;
    private final OutboxTransactionService outboxTransactionService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public OutboxKafkaPublisher(OutboxTransactionService outboxTransactionService,
            KafkaTemplate<String, String> kafkaTemplate, OutboxProperties outboxProperties,
            FlowTelemetry telemetry, FlowTracing tracing) {
        this.telemetry = telemetry;
        this.tracing = tracing;
        this.outboxTransactionService = outboxTransactionService;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxProperties = outboxProperties;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.outbox.publish-fixed-delay-ms:5000}"
    )
    public void publishPendingEvents() {
        List<OutboxEventEntity> events =
                outboxTransactionService.claimBatch(
                        outboxProperties.batchSize()
                );

        for (OutboxEventEntity event : events) {
            publishSingleEvent(event);
        }
    }

    @Scheduled(
            fixedDelayString =
                    "${app.outbox.recovery-fixed-delay-ms:60000}"
    )
    public void recoverStaleClaims() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(
                        outboxProperties.claimTimeoutMinutes()
                );

        telemetry.count("outbox", "recovered",
                outboxTransactionService.recoverStaleClaims(threshold));
    }

    @Scheduled(
            cron = "${app.outbox.cleanup-cron:0 0 3 * * *}"
    )
    public void cleanupSentEvents() {
        LocalDateTime threshold = LocalDateTime.now()
                .minusDays(
                        outboxProperties.sentRetentionDays()
                );

        outboxTransactionService.cleanupSentEvents(threshold);
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        String correlationId = event.getCorrelationId() == null
                ? event.getId().toString() : event.getCorrelationId();
        try (CorrelationContext ignored = CorrelationContext.open(
                correlationId, event.getId().toString())) {
            MDC.put("eventType", event.getEventType());
            MDC.put("topic", event.getTopic());
            MDC.put("attempt", Integer.toString(event.getAttempts() + 1));
            try (var trace = tracing.resume("outbox.publish", SpanKind.PRODUCER,
                    event.getTraceParent(), event.getTraceState())) {
                trace.tag("messaging.system", "kafka");
                trace.tag("messaging.destination.name", event.getTopic());
                trace.tag("messaging.operation.type", "send");
                trace.tag("outbox.attempt", Integer.toString(event.getAttempts() + 1));
                trace.tag("outbox.age.ms", Long.toString(Math.max(0,
                        java.time.Duration.between(event.getCreatedAt(),
                                LocalDateTime.now()).toMillis())));
                try {
                    publishWithContext(event, trace);
                } catch (RuntimeException exception) {
                    trace.error(exception);
                    throw exception;
                }
            }
        }
    }

    private void publishWithContext(OutboxEventEntity event, FlowTracing.TraceScope trace) {
        long started = System.nanoTime();
        telemetry.count("outbox", "attempt", 1);
        try {
            sendToKafka(event);
            boolean updated = outboxTransactionService.markSent(
                    event.getId(), event.getClaimToken());
            telemetry.record("outbox", updated ? "published" : "claim_lost", started, null);
            trace.tag("outbox.outcome", updated ? "published" : "claim_lost");
        } catch (Exception exception) {
            Throwable cause = unwrap(exception);
            trace.error(cause);

            boolean updated = outboxTransactionService.markFailed(
                    event.getId(),
                    event.getClaimToken(),
                    SafeFailure.describe(cause),
                    outboxProperties.maxAttempts()
            );
            String outcome = !updated ? "claim_lost"
                    : event.getAttempts() + 1 >= outboxProperties.maxAttempts() ? "dead" : "failed";
            telemetry.record("outbox", outcome, started, SafeFailure.describe(cause));
            trace.tag("outbox.outcome", outcome);
        }
    }

    private void sendToKafka(OutboxEventEntity event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        event.getTopic(),
                        event.getEventKey(),
                        event.getPayload()
                );

        record.headers().add(
                CorrelationContext.HEADER,
                CorrelationContext.currentOrNew().getBytes(StandardCharsets.UTF_8)
        );
        record.headers().add(
                EVENT_ID_HEADER,
                event.getId()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        tracing.headers().forEach((key, value) ->
                record.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record).join();
    }

    private Throwable unwrap(Exception exception) {
        if (exception
                instanceof java.util.concurrent.CompletionException
                && exception.getCause() != null) {
            return exception.getCause();
        }

        return exception;
    }

}

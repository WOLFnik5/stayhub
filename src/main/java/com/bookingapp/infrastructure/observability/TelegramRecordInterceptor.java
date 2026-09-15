package com.bookingapp.infrastructure.observability;

import io.opentelemetry.api.trace.SpanKind;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

@Component
public class TelegramRecordInterceptor implements RecordInterceptor<String, String> {
    private final FlowTelemetry telemetry;
    private final FlowTracing tracing;
    private final ThreadLocal<FlowTracing.TraceScope> trace = new ThreadLocal<>();
    private final ThreadLocal<CorrelationContext> context = new ThreadLocal<>();
    private final ThreadLocal<Long> started = new ThreadLocal<>();

    public TelegramRecordInterceptor(FlowTelemetry telemetry, FlowTracing tracing) {
        this.telemetry = telemetry;
        this.tracing = tracing;
    }

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
            Consumer<String, String> consumer) {
        context.set(openContext(record));
        trace.set(tracing.resume("telegram.consume", SpanKind.CONSUMER,
                header(record, "traceparent"), header(record, "tracestate")));
        trace.get().tag("messaging.system", "kafka");
        trace.get().tag("messaging.destination.name", record.topic());
        trace.get().tag("messaging.operation.type", "process");
        trace.get().tag("messaging.kafka.offset", Long.toString(record.offset()));
        trace.get().tag("messaging.destination.partition.id", Integer.toString(record.partition()));
        started.set(System.nanoTime());
        telemetry.count("consumer", "attempt", 1);
        return record;
    }

    public static CorrelationContext openContext(ConsumerRecord<?, ?> record) {
        String eventId = header(record, "eventId");
        String correlationId = header(record, CorrelationContext.HEADER);
        final CorrelationContext scope = CorrelationContext.open(
                correlationId == null ? eventId : correlationId,
                CorrelationContext.normalize(eventId));
        MDC.put("topic", record.topic());
        MDC.put("partition", Integer.toString(record.partition()));
        MDC.put("offset", Long.toString(record.offset()));
        return scope;
    }

    @Override
    public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        telemetry.record("consumer", "completed", started.get(), null);
        trace.get().tag("consumer.outcome", "completed");
    }

    @Override
    public void failure(ConsumerRecord<String, String> record, Exception exception,
            Consumer<String, String> consumer) {
        telemetry.record("consumer", "failed", started.get(), SafeFailure.describe(exception));
        trace.get().error(exception);
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record,
            Consumer<String, String> consumer) {
        FlowTracing.TraceScope currentTrace = trace.get();
        if (currentTrace != null) {
            currentTrace.close();
        }
        trace.remove();
        CorrelationContext scope = context.get();
        if (scope != null) {
            scope.close();
        }
        context.remove();
        started.remove();
    }

    public static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null ? null
                : new String(header.value(), StandardCharsets.UTF_8);
    }
}

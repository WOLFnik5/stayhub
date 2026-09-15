package com.bookingapp.infrastructure.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class FlowTracing {
    private static final W3CTraceContextPropagator PROPAGATOR =
            W3CTraceContextPropagator.getInstance();
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };
    private final Tracer tracer;

    public FlowTracing(OpenTelemetry telemetry) {
        tracer = telemetry.getTracer("booking-app.flow");
    }

    public TraceScope start(String name, SpanKind kind) {
        return createScope(name, kind, Context.current());
    }

    public TraceScope resume(String name, SpanKind kind, String traceparent, String tracestate) {
        Map<String, String> carrier = new HashMap<>();
        if (traceparent != null && traceparent.length() <= 55) {
            carrier.put("traceparent", traceparent);
        }
        if (tracestate != null && tracestate.length() <= 512) {
            carrier.put("tracestate", tracestate);
        }
        // A poller may have its own span. Stored messages must not inherit that unrelated parent.
        return createScope(name, kind, PROPAGATOR.extract(Context.root(), carrier, GETTER));
    }

    public Map<String, String> headers() {
        Map<String, String> carrier = new HashMap<>();
        PROPAGATOR.inject(Context.current(), carrier, Map::put);
        return carrier;
    }

    private TraceScope createScope(String name, SpanKind kind, Context parent) {
        return new TraceScope(tracer.spanBuilder(name).setSpanKind(kind).setParent(parent)
                .startSpan());
    }

    public static final class TraceScope implements AutoCloseable {
        private final Span span;
        private final Scope scope;
        private final String previousTraceId;
        private final String previousSpanId;

        private TraceScope(Span span) {
            this.span = span;
            previousTraceId = MDC.get("traceId");
            previousSpanId = MDC.get("spanId");
            scope = span.makeCurrent();
            if (span.getSpanContext().isValid()) {
                MDC.put("traceId", span.getSpanContext().getTraceId());
                MDC.put("spanId", span.getSpanContext().getSpanId());
            }
            tag("correlation.id", MDC.get(CorrelationContext.KEY));
            tag("messaging.message.id", MDC.get("eventId"));
        }

        public void tag(String name, String value) {
            if (value != null) {
                span.setAttribute(name, value);
            }
        }

        public void tag(String name, long value) {
            span.setAttribute(name, value);
        }

        public void error(Throwable failure) {
            error(SafeFailure.describe(failure));
        }

        public void error(String safeType) {
            span.setAttribute("error.type", safeType);
            span.setStatus(StatusCode.ERROR, safeType);
        }

        @Override
        public void close() {
            scope.close();
            restore("traceId", previousTraceId);
            restore("spanId", previousSpanId);
            span.end();
        }

        private void restore(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}

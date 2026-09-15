package com.bookingapp.infrastructure.observability;

import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationContext implements AutoCloseable {
    public static final String HEADER = "X-Correlation-ID";
    public static final String KEY = "correlationId";
    private final Map<String, String> previous;

    private CorrelationContext(String correlationId, String eventId) {
        previous = MDC.getCopyOfContextMap();
        MDC.put(KEY, normalize(correlationId));
        if (eventId != null) {
            MDC.put("eventId", eventId);
        }
    }

    public static CorrelationContext open(String correlationId, String eventId) {
        return new CorrelationContext(correlationId, eventId);
    }

    public static String normalize(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}")
                ? value : UUID.randomUUID().toString();
    }

    public static String currentOrNew() {
        return normalize(MDC.get(KEY));
    }

    @Override
    public void close() {
        if (previous == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(previous);
        }
    }
}

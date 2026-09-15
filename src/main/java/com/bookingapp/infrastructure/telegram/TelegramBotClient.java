package com.bookingapp.infrastructure.telegram;

import com.bookingapp.infrastructure.config.TelegramProperties;
import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.observability.SafeFailure;
import io.opentelemetry.api.trace.SpanKind;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TelegramBotClient {

    private final RestClient telegramRestClient;
    private final TelegramProperties telegramProperties;
    private final FlowTelemetry telemetry;
    private final FlowTracing tracing;

    public TelegramBotClient(
            RestClient telegramRestClient,
            TelegramProperties telegramProperties,
            FlowTelemetry telemetry,
            FlowTracing tracing
    ) {
        this.telegramRestClient = telegramRestClient;
        this.telegramProperties = telegramProperties;
        this.telemetry = telemetry;
        this.tracing = tracing;
    }

    public void sendMessage(String message) {
        try (var trace = tracing.start("telegram.send", SpanKind.CLIENT)) {
            trace.tag("http.request.method", "POST");
            trace.tag("server.address", URI.create(telegramProperties.getBaseUrl()).getHost());
            trace.tag("rpc.method", "sendMessage");
            sendMessage(message, trace);
        }
    }

    private void sendMessage(String message, FlowTracing.TraceScope trace) {
        long started = System.nanoTime();
        telemetry.count("telegram", "attempt", 1);
        try {
            var response = telegramRestClient.post()
                    .uri("/bot{token}/sendMessage", telegramProperties.getBotToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", telegramProperties.getChatId(),
                            "text", message
                    ))
                    .retrieve()
                    .toEntity(TelegramResponse.class);
            trace.tag("http.response.status_code", response.getStatusCode().value());
            if (response.getBody() == null || !Boolean.TRUE.equals(response.getBody().ok())) {
                throw new RestClientException("Telegram response did not confirm acceptance");
            }
            telemetry.record("telegram", "accepted", started, null);
            trace.tag("telegram.outcome", "accepted");
        } catch (RestClientException exception) {
            if (exception instanceof RestClientResponseException response) {
                trace.tag("http.response.status_code", response.getStatusCode().value());
            }
            String error = exception instanceof RestClientResponseException response
                    ? "HTTP_" + response.getStatusCode().value() : SafeFailure.describe(exception);
            telemetry.record("telegram", "failed", started, error);
            trace.error(error);
            // Do not retain the cause: Kafka/framework loggers may print its secret-bearing URL.
            throw new IllegalStateException("Telegram request failed: " + error);
        }
    }

    private record TelegramResponse(Boolean ok) {
    }
}

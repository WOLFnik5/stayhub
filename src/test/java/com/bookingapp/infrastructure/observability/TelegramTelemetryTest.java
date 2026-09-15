package com.bookingapp.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookingapp.infrastructure.config.TelegramProperties;
import com.bookingapp.infrastructure.telegram.TelegramBotClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramTelemetryTest {
    @Test
    void recordsAcceptanceAndSanitizesFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramProperties properties = new TelegramProperties();
        properties.setBotToken("secret-token");
        properties.setChatId("private-chat");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelegramBotClient client = new TelegramBotClient(builder.build(), properties, new FlowTelemetry(registry), new com.bookingapp.infrastructure.observability.FlowTracing(io.opentelemetry.api.OpenTelemetry.noop()));
        server.expect(requestTo("https://telegram.test/botsecret-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://telegram.test/botsecret-token/sendMessage"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-token private-chat"));
        client.sendMessage("private message");
        assertThatThrownBy(() -> client.sendMessage("private message"))
                .hasMessage("Telegram request failed: HTTP_401").hasNoCause();
        assertThat(registry.get("booking.flow.duration").tags("stage", "telegram", "outcome", "accepted")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("booking.flow.duration").tags("stage", "telegram", "outcome", "failed")
                .timer().count()).isEqualTo(1);
        server.verify();
    }

    @Test
    void doesNotReportAcceptanceForNegativeApiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramProperties properties = new TelegramProperties();
        properties.setBotToken("secret-token");
        properties.setChatId("private-chat");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TelegramBotClient client = new TelegramBotClient(builder.build(), properties, new FlowTelemetry(registry), new com.bookingapp.infrastructure.observability.FlowTracing(io.opentelemetry.api.OpenTelemetry.noop()));
        server.expect(requestTo("https://telegram.test/botsecret-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":false,\"description\":\"private\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.sendMessage("private message"))
                .hasMessage("Telegram request failed: RestClientException").hasNoCause();
        assertThat(registry.find("booking.flow.duration").tags("outcome", "accepted").timer()).isNull();
        server.verify();
    }
}

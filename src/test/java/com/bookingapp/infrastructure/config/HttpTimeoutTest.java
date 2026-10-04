package com.bookingapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.telegram.TelegramBotClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionRetrieveParams;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpTimeoutTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void telegramTimesOutBeforeHeadersAndDuringBodyWithoutLeakingSecrets(boolean sendHeaders)
            throws Exception {
        try (var server = new StalledServer(sendHeaders)) {
            var properties = new TelegramProperties();
            properties.setBaseUrl(server.baseUrl());
            properties.setBotToken("secret-token");
            properties.setChatId("private-chat");
            properties.setConnectTimeoutMs(500);
            properties.setReadTimeoutMs(150);
            var registry = new SimpleMeterRegistry();
            var client = new TelegramBotClient(
                    new TelegramConfiguration().telegramRestClient(properties), properties,
                    new FlowTelemetry(registry), new FlowTracing(OpenTelemetry.noop()));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> client.sendMessage("private message"))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageStartingWith("Telegram request failed:")
                            .hasMessageNotContaining("secret-token")
                            .hasMessageNotContaining("private-chat")
                            .hasNoCause());
            assertThat(server.requests.get()).isEqualTo(1);
            assertThat(registry.get("booking.flow.duration")
                    .tags("stage", "telegram", "outcome", "failed").timer().count()).isEqualTo(1);
            assertThat(registry.find("booking.flow.duration")
                    .tags("stage", "telegram", "outcome", "accepted").timer()).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stripeTimesOutBeforeHeadersAndDuringBodyWithoutAutomaticRetry(boolean sendHeaders)
            throws Exception {
        try (var server = new StalledServer(sendHeaders)) {
            var properties = new StripeProperties();
            properties.setSecretKey("sk_test_local_only");
            properties.setConnectTimeoutMs(500);
            properties.setReadTimeoutMs(150);
            var client = new StripeConfiguration().stripeClient(properties);
            var options = RequestOptions.builder().setBaseUrl(server.baseUrl()).build();

            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> client.checkout().sessions().retrieve(
                            "cs_test", (SessionRetrieveParams) null, options))
                            .isInstanceOf(ApiConnectionException.class));
            assertThat(server.requests.get()).isEqualTo(1);
        }
    }

    private static class StalledServer implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor =
                Executors.newCachedThreadPool();
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger requests = new AtomicInteger();

        StalledServer(boolean sendHeaders) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                try (exchange) {
                    exchange.getRequestBody().readAllBytes();
                    if (sendHeaders) {
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, 100);
                        exchange.getResponseBody().write("{\"ok\":".getBytes(StandardCharsets.UTF_8));
                        exchange.getResponseBody().flush();
                    }
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}

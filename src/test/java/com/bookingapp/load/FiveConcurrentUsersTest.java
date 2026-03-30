package com.bookingapp.load;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class FiveConcurrentUsersTest {

    private static final String PASSWORD = "TestPass123!";

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("booking_app_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.listener.auto-startup", () -> false);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpClient client;

    @BeforeEach
    void setUp() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    void shouldHandleFiveConcurrentUsers() throws Exception {
        try (var executor = Executors.newFixedThreadPool(5)) {
            List<Future<ScenarioResult>> futures = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                final int userNumber = i;
                futures.add(executor.submit(() -> runScenario(userNumber)));
            }

            for (Future<ScenarioResult> future : futures) {
                ScenarioResult result = future.get();
                assertTrue(result.success(), result.message());
            }
        }
    }

    private ScenarioResult runScenario(int userNumber) {
        try {
            String email = uniqueEmail(userNumber);

            HttpResponse<String> registerResponse = client.send(
                    registerRequest(email, PASSWORD, userNumber),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (registerResponse.statusCode() != 201
                    && registerResponse.statusCode() != 400
                    && registerResponse.statusCode() != 409) {
                return fail("Register failed for %s status=%s body=%s"
                        .formatted(email, registerResponse.statusCode(), registerResponse.body()));
            }

            HttpResponse<String> loginResponse = client.send(
                    loginRequest(email, PASSWORD),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (loginResponse.statusCode() != 200) {
                return fail("Login failed for %s status=%s body=%s"
                        .formatted(email, loginResponse.statusCode(), loginResponse.body()));
            }

            String token = extractAccessToken(loginResponse.body());
            if (token == null || token.isBlank()) {
                return fail("Token missing for %s body=%s".formatted(email, loginResponse.body()));
            }

            ScenarioResult health = expect2xx(getRequest("/health", null), "GET /health");
            if (!health.success()) return health;

            ScenarioResult accommodations = expect2xx(getRequest("/accommodations", null), "GET /accommodations");
            if (!accommodations.success()) return accommodations;

            ScenarioResult bookings = expect2xx(getRequest("/bookings/my", token), "GET /bookings/my");
            if (!bookings.success()) return bookings;

            ScenarioResult payments = expect2xx(getRequest("/payments", token), "GET /payments");
            if (!payments.success()) return payments;

            return ok();
        } catch (Exception e) {
            return fail("Scenario failed for user " + userNumber + ": " + e.getMessage());
        }
    }

    private HttpRequest registerRequest(String email, String password, int userNumber) {
        String body = """
                {
                  "email": "%s",
                  "firstName": "Load",
                  "lastName": "User%d",
                  "password": "%s"
                }
                """.formatted(email, userNumber, password);

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpRequest loginRequest(String email, String password) {
        String body = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpRequest getRequest(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .GET();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder.build();
    }

    private ScenarioResult expect2xx(HttpRequest request, String label) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return ok();
        }
        return fail("%s failed status=%s body=%s"
                .formatted(label, response.statusCode(), response.body()));
    }

    private String extractAccessToken(String responseBody) throws IOException {
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode tokenNode = json.get("accessToken");
        return tokenNode == null ? null : tokenNode.asText();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String uniqueEmail(int userNumber) {
        return "loaduser" + userNumber + "+" + UUID.randomUUID() + "@example.com";
    }

    private ScenarioResult ok() {
        return new ScenarioResult(true, "OK");
    }

    private ScenarioResult fail(String message) {
        return new ScenarioResult(false, message);
    }

    private record ScenarioResult(boolean success, String message) {
    }
}

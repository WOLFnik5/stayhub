package com.bookingapp.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Explicitly enabled experiment; never adds benchmark latency to normal CI. */
@EnabledIfSystemProperty(named = "stayhub.performance", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ReadPerformanceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @LocalServerPort
    int port;
    @Autowired
    DataSource dataSource;
    @Autowired
    JwtTokenService tokens;
    @Autowired
    UserRepositoryImpl users;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> accessTokens = new ArrayList<>();

    @Test
    void measureReadsAndCandidateIndex() throws Exception {
        int seconds = Integer.getInteger("stayhub.performance.seconds", 15);
        assertThat(seconds).isBetween(1, 300);
        seed();
        // Remove only this optimization in the disposable DB to reproduce its baseline.
        execute("DROP INDEX IF EXISTS idx_bookings_user_checkin_id");
        for (long id = 1; id <= 200; id++) {
            accessTokens.add(tokens.generateToken(users.findById(id).orElseThrow()));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", Instant.now().toString());
        report.put("java", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("postgresImage", POSTGRES.getDockerImageName());
        report.put("workload", "Closed-loop reads; client and app on same host; no Kafka/Stripe/Telegram traffic");
        report.put("fixture", Map.of("users", 200, "accommodations", 5000,
                "bookings", 100000, "bookingStatus", "CANCELED"));
        report.put("stageSeconds", seconds);
        report.put("hikariMaximumPoolSize", ((com.zaxxer.hikari.HikariDataSource) dataSource).getMaximumPoolSize());
        report.put("beforePlans", plans());
        report.put("baseline", stages(seconds));
        write(report);
        execute("CREATE INDEX perf_bookings_user_dates ON bookings (user_id, check_in_date ASC, id DESC)");
        execute("ANALYZE bookings");
        report.put("candidateIndex", "bookings (user_id, check_in_date ASC, id DESC)");
        report.put("afterPlans", plans());
        report.put("candidate", stages(seconds));
        report.put("finishedAt", Instant.now().toString());
        write(report);
        System.out.println("Performance report: target/performance/read-performance.json");
        for (String phase : List.of("baseline", "candidate")) {
            JsonNode stages = mapper.valueToTree(report.get(phase));
            for (JsonNode stage : stages) {
                for (JsonNode endpoint : stage.get("endpoints")) {
                    assertThat(endpoint.get("errors").asLong()).as(phase + " HTTP errors").isZero();
                }
            }
        }
    }

    private void seed() throws Exception {
        execute("""
                INSERT INTO users (email, first_name, last_name, password, role)
                SELECT 'perf' || n || '@example.com', 'Perf', 'User', 'unused', 'CUSTOMER'
                FROM generate_series(1, 200) n
                """);
        execute("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                SELECT 'APARTMENT', 'Warsaw ' || n, '40m2', 100, 10
                FROM generate_series(1, 5000) n
                """);
        execute("""
                INSERT INTO bookings (check_in_date, check_out_date, accommodation_id, user_id, status)
                SELECT DATE '2030-01-01' + (n % 365), DATE '2030-01-02' + (n % 365),
                       1 + n % 5000, 1 + n % 200, 'CANCELED'
                FROM generate_series(1, 100000) n
                """);
        execute("ANALYZE");
    }

    private Map<String, Object> plans() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var query : Map.of(
                "myBookingsPage", "SELECT * FROM bookings WHERE user_id = 1 ORDER BY check_in_date ASC, id DESC LIMIT 20",
                "myBookingsCount", "SELECT COUNT(*) FROM bookings WHERE user_id = 1",
                "availablePage", "SELECT id FROM accommodations WHERE availability > 0 ORDER BY id LIMIT 20").entrySet()) {
            List<JsonNode> repetitions = new ArrayList<>();
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                for (int i = 0; i < 5; i++) {
                    try (var rows = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + query.getValue())) {
                        rows.next();
                        repetitions.add(mapper.readTree(rows.getString(1)));
                    }
                }
            }
            result.put(query.getKey(), Map.of("sql", query.getValue(), "repetitions", repetitions));
        }
        return result;
    }

    private List<Map<String, Object>> stages(int seconds) throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int clients : List.of(5, 20, 50)) {
            runStage(clients, 3); // Exclude warm-up from the report.
            var result = runStage(clients, seconds);
            results.add(result);
            System.out.println("Read benchmark: " + mapper.writeValueAsString(result));
        }
        return results;
    }

    private Map<String, Object> runStage(int clients, int seconds) throws Exception {
        List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                var executor = Executors.newFixedThreadPool(clients)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            long[] deadline = new long[1];
            for (int i = 0; i < clients; i++) {
                int user = i;
                tasks.add(() -> {
                    start.await();
                    int sequence = 0;
                    while (System.nanoTime() < deadline[0]) {
                        String endpoint = sequence++ % 2 == 0 ? "/accommodations" : "/bookings/my";
                        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + endpoint))
                                .header("Authorization", "Bearer " + accessTokens.get(user))
                                .timeout(Duration.ofSeconds(10)).GET().build();
                        long begun = System.nanoTime();
                        int status;
                        try {
                            status = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
                        } catch (java.io.IOException exception) {
                            status = 0;
                        }
                        samples.add(new Sample(endpoint, (System.nanoTime() - begun) / 1_000_000.0, status));
                    }
                    return null;
                });
            }
            var futures = tasks.stream().map(executor::submit).toList();
            long begun = System.nanoTime();
            deadline[0] = begun + Duration.ofSeconds(seconds).toNanos();
            start.countDown();
            for (var future : futures) {
                future.get(seconds + 15L, java.util.concurrent.TimeUnit.SECONDS);
            }
            double elapsed = (System.nanoTime() - begun) / 1_000_000_000.0;
            List<Map<String, Object>> endpoints = new ArrayList<>();
            for (String endpoint : List.of("/accommodations", "/bookings/my")) {
                List<Sample> matching = samples.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
                double[] latency = matching.stream().mapToDouble(Sample::milliseconds).sorted().toArray();
                long errors = matching.stream().filter(s -> s.status() != 200).count();
                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("endpoint", endpoint);
                metrics.put("requests", matching.size());
                metrics.put("errors", errors);
                metrics.put("errorRate", matching.isEmpty() ? 0 : (double) errors / matching.size());
                metrics.put("requestsPerSecond", matching.size() / elapsed);
                metrics.put("p50Ms", percentile(latency, 0.50));
                metrics.put("p95Ms", percentile(latency, 0.95));
                metrics.put("p99Ms", percentile(latency, 0.99));
                endpoints.add(metrics);
            }
            return Map.of("clients", clients, "elapsedSeconds", elapsed, "endpoints", endpoints);
        }
    }

    private double percentile(double[] values, double quantile) {
        return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * quantile) - 1];
    }

    private void execute(String sql) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void write(Map<String, Object> report) throws Exception {
        Path directory = Path.of("target", "performance");
        Files.createDirectories(directory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("read-performance.json").toFile(), report);
    }

    private record Sample(String endpoint, double milliseconds, int status) {
    }
}

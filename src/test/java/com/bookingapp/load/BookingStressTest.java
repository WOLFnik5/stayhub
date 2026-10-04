package com.bookingapp.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingapp.infrastructure.outbox.OutboxKafkaPublisher;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Open-loop mixed traffic and a separate capacity race on a disposable database. */
@EnabledIfSystemProperty(named = "stayhub.stress", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class BookingStressTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () ->
                Integer.getInteger("stayhub.stress.pool-size", 10));
    }

    @LocalServerPort
    int port;
    @Autowired
    HikariDataSource dataSource;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtTokenService tokens;
    @Autowired
    UserRepositoryImpl users;
    @Autowired
    MeterRegistry meters;
    // Keep real transactional event persistence, but isolate Kafka delivery from API/DB stress.
    @MockitoBean
    OutboxKafkaPublisher delivery;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> accessTokens = new ArrayList<>();
    private final AtomicLong writeSequence = new AtomicLong();
    private final AtomicLong submittedWrites = new AtomicLong();
    private final AtomicLong successfulWrites = new AtomicLong();
    private final LocalDate bookingDate = LocalDate.now().plusYears(1);

    @Test
    void stressMixedTrafficAndPreserveCapacityAndOutbox() throws Exception {
        int seconds = Integer.getInteger("stayhub.stress.seconds", 10);
        int maxInFlight = Integer.getInteger("stayhub.stress.max-in-flight", 256);
        int warmupSeconds = Integer.getInteger("stayhub.stress.warmup-seconds", 3);
        int warmupRate = Integer.getInteger("stayhub.stress.warmup-rate", 100);
        int poolSize = Integer.getInteger("stayhub.stress.pool-size", 10);
        int[] rates = Arrays.stream(System.getProperty("stayhub.stress.rates", "50,150,300,600,1200,2400,4800")
                .split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
        assertThat(seconds).isBetween(1, 300);
        assertThat(maxInFlight).isBetween(1, 2000);
        assertThat(warmupSeconds).isBetween(1, 300);
        assertThat(warmupRate).isBetween(1, 10000);
        assertThat(poolSize).isBetween(1, 80);
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(poolSize);
        assertThat(rates.length).isBetween(1, 20);
        for (int rate : rates) {
            assertThat(rate).isBetween(1, 10000);
        }
        seed();
        for (long id = 1; id <= 200; id++) {
            accessTokens.add(tokens.generateToken(users.findById(id).orElseThrow()));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", Instant.now().toString());
        report.put("java", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("postgresVersion", jdbc.queryForObject("SELECT version()", String.class));
        report.put("hikariMaximumPoolSize", dataSource.getMaximumPoolSize());
        report.put("hikariMinimumIdle", dataSource.getMinimumIdle());
        report.put("hikariConnectionTimeoutMs", dataSource.getConnectionTimeout());
        report.put("warmupSeconds", warmupSeconds);
        report.put("warmupRate", warmupRate);
        report.put("maxInFlight", maxInFlight);
        report.put("stageSeconds", seconds);
        report.put("arrivalScheduler", "Spin wait for final 25ms to avoid coarse sleep timers; consumes a client CPU core");
        report.put("rates", rates);
        report.put("fixture", Map.of("users", 200, "accommodations", 5000, "canceledBookings", 100000));
        report.put("workload", "Open-loop: 40% accommodations, 40% own bookings, 20% create booking; real Outbox persistence, mocked Kafka delivery");
        List<Map<String, Object>> stages = new ArrayList<>();
        report.put("stages", stages);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            report.put("capacityRace", capacityRace(client));
            write(report);
            report.put("warmup", runStage(client, warmupRate, warmupSeconds, maxInFlight));
            // Warm-up writes remain in DB and integrity counters, outside measured stages.
            for (int rate : rates) {
                Map<String, Object> stage = runStage(client, rate, seconds, maxInFlight);
                stages.add(stage);
                write(report);
                System.out.println("Mixed stress: " + mapper.writeValueAsString(stage));
                if (Boolean.TRUE.equals(stage.get("thresholdBreached"))) {
                    report.put("stoppedAtRate", rate);
                    break;
                }
            }
            waitForDatabaseDrain();
            var health = client.send(HttpRequest.newBuilder(uri("/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            report.put("recoveryHealthStatus", health.statusCode());
            long writes = jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE accommodation_id <= 5000 AND status = 'PENDING'", Long.class);
            long events = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'BookingCreatedEvent'", Long.class);
            long duplicateEvents = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT aggregate_id FROM outbox_events WHERE event_type = 'BookingCreatedEvent'
                        GROUP BY aggregate_id HAVING COUNT(*) > 1
                    ) duplicates
                    """, Long.class);
            long overCapacity = jdbc.queryForObject("""
                    WITH boundaries AS (
                        SELECT accommodation_id, check_in_date AS day, 1 AS delta FROM bookings
                        WHERE status NOT IN ('CANCELED', 'EXPIRED')
                        UNION ALL
                        SELECT accommodation_id, check_out_date AS day, -1 AS delta FROM bookings
                        WHERE status NOT IN ('CANCELED', 'EXPIRED')
                    ), daily AS (
                        SELECT accommodation_id, day, SUM(delta) AS delta FROM boundaries
                        GROUP BY accommodation_id, day
                    ), occupancy AS (
                        SELECT accommodation_id, SUM(delta) OVER (PARTITION BY accommodation_id ORDER BY day) AS occupied
                        FROM daily
                    ) SELECT COUNT(*) FROM occupancy o JOIN accommodations a ON a.id = o.accommodation_id
                      WHERE o.occupied > a.availability
                    """, Long.class);
            report.put("integrity", Map.of("submittedWrites", submittedWrites.get(),
                    "successfulWriteResponses", successfulWrites.get(), "committedMixedBookings", writes,
                    "createdEventsIncludingCapacityRace", events, "duplicateEvents", duplicateEvents,
                    "overCapacityDates", overCapacity));
            report.put("finishedAt", Instant.now().toString());
            write(report);
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(writes).isBetween(successfulWrites.get(), submittedWrites.get());
            assertThat(events).isEqualTo(writes + 5);
            assertThat(duplicateEvents).isZero();
            assertThat(overCapacity).isZero();
        }
    }

    private Map<String, Object> capacityRace(HttpClient client) throws Exception {
        long accommodation = jdbc.queryForObject("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                VALUES ('APARTMENT', 'Capacity race', '40m2', 100, 5) RETURNING id
                """, Long.class);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, 100).mapToObj(i -> executor.submit(() -> {
                start.await();
                return client.send(bookingRequest(accommodation, bookingDate, i), HttpResponse.BodyHandlers.ofString()).statusCode();
            })).toList();
            start.countDown();
            for (var future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS));
            }
        }
        long committed = jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE accommodation_id = ?", Long.class, accommodation);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(5);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(95);
        assertThat(committed).isEqualTo(5);
        return Map.of("requests", 100, "capacity", 5, "statusCounts", statusCounts(statuses), "committed", committed);
    }

    private Map<String, Object> runStage(HttpClient client, int rate, int seconds, int maximum) throws Exception {
        ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
        AtomicInteger peakActive = new AtomicInteger();
        AtomicInteger peakPending = new AtomicInteger();
        AtomicLong poolSamples = new AtomicLong();
        AtomicLong pendingSum = new AtomicLong();
        Timer acquisition = meters.find("hikaricp.connections.acquire").timer();
        Timer usage = meters.find("hikaricp.connections.usage").timer();
        long acquisitionsBefore = acquisition == null ? 0 : acquisition.count();
        double acquireTimeBefore = acquisition == null ? 0 : acquisition.totalTime(TimeUnit.MILLISECONDS);
        long usesBefore = usage == null ? 0 : usage.count();
        double useTimeBefore = usage == null ? 0 : usage.totalTime(TimeUnit.MILLISECONDS);
        Semaphore slots = new Semaphore(maximum);
        int dropped = 0;
        int generatorMissed = 0;
        long interval = 1_000_000_000L / rate;
        long start = System.nanoTime();
        int offered = rate * seconds;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var monitor = Executors.newSingleThreadScheduledExecutor()) {
            monitor.scheduleAtFixedRate(() -> {
                var pool = dataSource.getHikariPoolMXBean();
                peakActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
                peakPending.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
                pendingSum.addAndGet(pool.getThreadsAwaitingConnection());
                poolSamples.incrementAndGet();
            }, 0, 50, TimeUnit.MILLISECONDS);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int n = 0; n < offered; n++) {
                long planned = start + n * interval;
                while (System.nanoTime() < planned) {
                    long remaining = planned - System.nanoTime();
                    if (remaining > TimeUnit.MILLISECONDS.toNanos(25)) {
                        LockSupport.parkNanos(remaining - TimeUnit.MILLISECONDS.toNanos(25));
                    } else {
                        Thread.onSpinWait();
                    }
                }
                if (System.nanoTime() - planned > interval * 5) {
                    generatorMissed++;
                    continue; // Do not catch up with a burst when the generator falls behind.
                }
                if (!slots.tryAcquire()) {
                    dropped++;
                    continue;
                }
                int sequence = n;
                futures.add(executor.submit(() -> {
                    String operation = sequence % 5 == 4 ? "create" : sequence % 5 < 2 ? "accommodations" : "history";
                    long begun = System.nanoTime();
                    int status = 0;
                    String failure = "";
                    try {
                        HttpRequest request;
                        if (operation.equals("create")) {
                            long write = writeSequence.getAndIncrement();
                            request = bookingRequest(1 + write % 1000, bookingDate.plusDays(write / 1000), sequence % 200);
                            submittedWrites.incrementAndGet();
                        } else {
                            request = HttpRequest.newBuilder(uri(operation.equals("history") ? "/bookings/my" : "/accommodations"))
                                    .header("Authorization", "Bearer " + accessTokens.get(sequence % 200))
                                    .timeout(Duration.ofSeconds(3)).GET().build();
                        }
                        status = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
                        if (operation.equals("create") && status == 200) {
                            successfulWrites.incrementAndGet();
                        }
                    } catch (Exception exception) {
                        failure = exception.getClass().getSimpleName();
                        if (exception instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                    } finally {
                        long end = System.nanoTime();
                        samples.add(new Sample(operation, status, failure, (end - begun) / 1_000_000.0, (end - planned) / 1_000_000.0));
                        slots.release();
                    }
                }));
            }
            for (var future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
            monitor.shutdownNow();
        }
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        List<Map<String, Object>> operations = new ArrayList<>();
        for (String operation : List.of("accommodations", "history", "create")) {
            List<Sample> matching = samples.stream().filter(s -> s.operation().equals(operation)).toList();
            double[] latency = matching.stream().mapToDouble(Sample::arrivalMilliseconds).sorted().toArray();
            double[] service = matching.stream().mapToDouble(Sample::serviceMilliseconds).sorted().toArray();
            operations.add(Map.of("operation", operation, "requests", matching.size(),
                    "statuses", statusCounts(matching.stream().map(Sample::status).toList()),
                    "failures", matching.stream().filter(s -> !s.failure().isEmpty()).collect(Collectors.groupingBy(Sample::failure, Collectors.counting())),
                    "p50ArrivalMs", percentile(latency, .50), "p95ArrivalMs", percentile(latency, .95),
                    "p99ArrivalMs", percentile(latency, .99), "p95ServiceMs", percentile(service, .95)));
        }
        long errors = samples.stream().filter(s -> s.status() != 200).count();
        double[] allLatency = samples.stream().mapToDouble(Sample::arrivalMilliseconds).sorted().toArray();
        double errorRate = samples.isEmpty() ? 0 : (double) errors / samples.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetRequestsPerSecond", rate);
        result.put("offered", offered);
        result.put("submitted", samples.size());
        result.put("generatorMissed", generatorMissed);
        result.put("generatorMissRate", (double) generatorMissed / offered);
        result.put("loadGeneratorLimited", (double) generatorMissed / offered > .01);
        result.put("submittedRequestsPerSecondDuringArrivalWindow", (double) samples.size() / seconds);
        result.put("clientInFlightLimitDrops", dropped);
        result.put("elapsedIncludingDrainSeconds", elapsed);
        result.put("successfulRequestsPerSecondIncludingDrain", (samples.size() - errors) / elapsed);
        result.put("httpErrorRate", errorRate);
        result.put("peakHikariActive", peakActive.get());
        result.put("peakHikariPending", peakPending.get());
        result.put("meanSampledHikariPending", poolSamples.get() == 0 ? 0 : (double) pendingSum.get() / poolSamples.get());
        if (acquisition != null && acquisition.count() > acquisitionsBefore) {
            result.put("hikariAcquisitions", acquisition.count() - acquisitionsBefore);
            result.put("meanHikariAcquireMs", (acquisition.totalTime(TimeUnit.MILLISECONDS) - acquireTimeBefore)
                    / (acquisition.count() - acquisitionsBefore));
        }
        if (usage != null && usage.count() > usesBefore) {
            result.put("meanHikariConnectionUseMs", (usage.totalTime(TimeUnit.MILLISECONDS) - useTimeBefore)
                    / (usage.count() - usesBefore));
        }
        result.put("operations", operations);
        List<String> reasons = new ArrayList<>();
        if (errorRate > .01) {
            reasons.add("HTTP errors exceed 1%");
        }
        if ((double) dropped / offered > .01) {
            reasons.add("client in-flight cap drops exceed 1%");
        }
        if (percentile(allLatency, .95) > 1000) {
            reasons.add("arrival p95 exceeds 1000ms");
        }
        result.put("thresholdReasons", reasons);
        result.put("thresholdBreached", !reasons.isEmpty());
        return result;
    }

    private HttpRequest bookingRequest(long accommodation, LocalDate date, int user) throws Exception {
        return HttpRequest.newBuilder(uri("/bookings"))
                .header("Authorization", "Bearer " + accessTokens.get(user))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "accommodationId", accommodation, "checkInDate", date.toString(), "checkOutDate", date.plusDays(1).toString()))))
                .build();
    }

    private void seed() {
        jdbc.update("""
                INSERT INTO users (email, first_name, last_name, password, role)
                SELECT 'stress' || n || '@example.com', 'Stress', 'User', 'unused', 'CUSTOMER'
                FROM generate_series(1, 200) n
                """);
        jdbc.update("""
                INSERT INTO accommodations (type, location, size, daily_rate, availability)
                SELECT 'APARTMENT', 'Warsaw ' || n, '40m2', 100, 10 FROM generate_series(1, 5000) n
                """);
        jdbc.update("""
                INSERT INTO bookings (check_in_date, check_out_date, accommodation_id, user_id, status)
                SELECT DATE '2030-01-01' + (n % 365), DATE '2030-01-02' + (n % 365),
                       1 + n % 5000, 1 + n % 200, 'CANCELED' FROM generate_series(1, 100000) n
                """);
        jdbc.execute("ANALYZE");
    }

    private void waitForDatabaseDrain() {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (dataSource.getHikariPoolMXBean().getActiveConnections() > 0 && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).as("database drained after stress").isZero();
    }

    private Map<Integer, Long> statusCounts(List<Integer> statuses) {
        return statuses.stream().collect(Collectors.groupingBy(s -> s, java.util.TreeMap::new, Collectors.counting()));
    }

    private double percentile(double[] values, double quantile) {
        return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * quantile) - 1];
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private void write(Map<String, Object> report) throws Exception {
        Path directory = Path.of("target", "performance");
        Files.createDirectories(directory);
        String name = System.getProperty("stayhub.stress.report-name", "booking-stress");
        assertThat(name).matches("[a-zA-Z0-9_-]+");
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name + ".json").toFile(), report);
    }

    private record Sample(String operation, int status, String failure,
            double serviceMilliseconds, double arrivalMilliseconds) {
    }
}

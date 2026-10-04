# Hikari connection budget experiment

## Reproduce

JDK 21, Maven, Docker, and PowerShell:

```powershell
./ops/performance/Compare-Hikari.ps1
```

The script runs `BookingStressTest` sequentially in ABBA order: maximum pool sizes
10, 20, 20, 10. Each trial starts a new JVM and disposable PostgreSQL 17 container,
applies migrations, and seeds the same 200 customers, 5,000 accommodations, and
100,000 canceled bookings. The Compose database is untouched.

Each trial uses a ten-second warm-up at 600 offered requests/second, then the same
configured 1200/1800/2400 rates for 15 seconds each. The workload is 40% public
accommodation reads (with a bearer token), 40% own booking-history reads, and 20%
booking writes including transactional Outbox persistence. Background Kafka
delivery is mocked; HTTP/service/database logic remains real.

Every trial retains the existing stress stop conditions. If a pool hits the
client-cap/error/latency guardrail sooner, later stages are absent from that trial.
Compare rates present in both pools; do not treat an absent stage as zero throughput.
Within each compared rate, preceding offered rates and fixture shape are the same,
though the number of committed warm-up/prior writes can differ after skipped slots.

Both maximum size and default minimum idle increase together (10/10 versus 20/20).
The connection-acquisition timeout remains 30 seconds and HTTP deadline remains
three seconds. This is a comparison of connection budgets, not an experiment that
isolates maximum size from minimum idle or changes timeout behavior.

Optional parameters:

```powershell
./ops/performance/Compare-Hikari.ps1 -Seconds 30 -Rates '1200,1800,2400' -WarmupSeconds 15 -WarmupRate 600
```

Raw reports/logs are in `target/performance/hikari-10-a.*`, `hikari-20-a.*`,
`hikari-20-b.*`, and `hikari-10-b.*`. The combined artifact is
`target/performance/hikari-comparison.json`. Preserve evidence before `mvn clean`.

## Evaluate

Use actual submitted rate, successful completions including drain, per-operation
p95/p99, generator misses, client-cap drops, sampled pending threads, and HTTP errors.
Every completed trial also verifies capacity, booking/Outbox reconciliation,
absence of duplicate events, and health recovery.

Per-stage Hikari timer deltas expose mean acquisition time (connection wait plus
acquisition overhead) and mean use time (connection held). These are means over
connection borrows, not per-request time or percentile values; a request may borrow
more than once. The pending queue is sampled every 50 ms and can miss short spikes.

ABBA reduces simple ordering bias but two runs per size do not provide a confidence
interval. Generator, application, console logging, and database share host resources;
the generator reserves CPU for precise arrivals. JaCoCo and logging remain enabled.
Report an observed range rather than one preferred run or a global capacity claim.

Changing the pool budget is justified only if repeated comparisons improve the
relevant latency/throughput without worsening correctness, error rates, or database
contention. A shorter acquisition wait alongside longer connection-use time can
indicate that waiting moved into PostgreSQL rather than disappearing.

Application pool size can already be configured through Spring Boot's
`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` environment variable. Budget connections
across application instances and background work against PostgreSQL's limit;
this single-instance experiment does not validate a multi-instance rollout.

## Recorded comparison — 2026-10-03 (Europe/Warsaw)

[Combined raw evidence](hikari-comparison-2026-10-03.json). Four sequential trials
completed with JDK 21.0.8, Windows 11, PostgreSQL 17.9, and 16 available host
processors. Configured rate stages were 1200/1800/2400, 15 seconds each, after a
ten-second warm-up at 600. The table compares the 1200 target, present in all runs.

| Trial | Maximum/minimum idle | Actual submitted req/s | Create p95 ms | Mean acquisition ms | Mean connection use ms | Client-cap drops | Generator misses |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10-A | 10/10 | 1194.5 | 171.41 | 22.23 | 3.02 | 0 | 82 |
| 20-A | 20/20 | 1041.9 | 449.05 | 42.91 | 7.81 | 2076 | 296 |
| 20-B | 20/20 | 1196.7 | 38.16 | 1.17 | 3.22 | 0 | 50 |
| 10-B | 10/10 | 1197.6 | 30.90 | 1.07 | 2.30 | 0 | 36 |

All stages in all four runs had zero HTTP errors among submitted requests.
Both 10-connection trials stopped at 1800 because client-cap drops exceeded 1%.
20-A stopped at 1200 for that reason; 20-B continued through 1800 (253 cap drops,
below 1% of 27000 offered slots) and stopped at 2400 (6344 cap drops).
These drops occurred in the bounded client, not as HTTP rejections by the API.
20-A also missed more than 1% of generator slots at 1200, further limiting claims
about a nominal-rate comparison. Actual submitted rates are shown explicitly.

20-B shows a possible gain at 1800: mean acquisition 9.49 ms versus 43.38–53.54 ms
for the 10-connection runs, with create p95 157.47 ms versus 179.92–198.88 ms.
However, 20-A did not reach that stage and performed substantially worse at 1200.
The improvement was not reproduced across both 20-connection runs, so it is not
sufficient evidence to change the application default.

**Decision: retain the standard maximum pool size 10.** Test-only parameters,
timers, and the repeatable comparison script are added; application runtime pool
configuration is unchanged. This is an evidence-limited decision, not a proof
that 10 is globally optimal or 20 is always worse.

Within-size variation is large for both pools. Longer connection-use time in
20-A is consistent with pressure while connections are held, but the experiment
does not identify PostgreSQL, CPU, GC, logging, or host contention as the sole
cause. A next diagnostic should capture those signals under sustained equal
submitted load on a separate generator before revisiting the pool budget.

Every trial passed its capacity race (5 accepted / 95 conflicts), reconciliation
of successful booking writes with persisted rows and created Outbox events, zero
duplicate events, zero occupancy above capacity, and post-drain health 200.
The mixed committed write counts were respectively 9963, 4318, 15729, and 10068;
each had five additional capacity-race bookings/events. There were no ambiguous
write outcomes in these runs.

Validation: all four opt-in trials passed. The subsequent normal `mvn verify`
also passed with 277 successful tests, two intentionally skipped benchmarks,
zero Checkstyle violations, and a passing JaCoCo coverage gate.

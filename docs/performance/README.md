# StayHub performance experiments

For open-loop traffic including booking writes, capacity checks, and Outbox
integrity, see [mixed stress methodology and results](STRESS.md).
For controlled comparisons of connection budgets, see [Hikari measurements](HIKARI.md).

## Reproduce

Use JDK 21, Maven, and a running Docker daemon:

```powershell
mvn '-Dtest=ReadPerformanceTest' '-Dstayhub.performance=true' test
```

Normal `mvn test` / CI skips this opt-in benchmark. The experiment starts PostgreSQL
17 in a disposable container, applies the real Liquibase migrations, and starts the
application on a random HTTP port. It does not read or modify your Compose database.

The report is written to `target/performance/read-performance.json`. Save it before
`mvn clean`. Use `-Dstayhub.performance.seconds=60` for longer stages (maximum 300).

## Workload and interpretation

- 200 customers, 5,000 available accommodations, 100,000 canceled bookings
  (500 bookings per customer). This fixture exercises history pagination; it does
  not benchmark capacity checks or booking/payment writes.
- JWTs are generated before measurement. Registration, BCrypt login, Stripe,
  Telegram delivery, and Kafka processing are outside the measured scenario.
- Every client alternates `GET /accommodations` and authenticated `GET /bookings/my`.
  Default stages: 5, 20, and 50 clients, 3 seconds warm-up and 15 seconds measurement.
- Closed-loop clients send the next request after the prior response. Client and
  application share a machine, with PostgreSQL in Docker. This is a local baseline,
  not a capacity guarantee or an open-loop overload/stress test.
- Latencies include response body consumption and failed requests. Status other
  than 200 or an I/O timeout counts as an error; the experiment fails if errors occur.
- Throughput uses actual elapsed time, including outstanding requests at stage end.
  Quantiles use nearest-rank samples. Only p50/p95/p99 are reported, not averages of quantiles.
- JaCoCo, warm caches, JIT, container resources, host load, and execution order affect
  results. Repeat runs and reverse the index order before attributing HTTP changes
  solely to the index. The standard Hikari maximum size is recorded, not tuned.

## PostgreSQL experiment

The report stores five plans per query before/after the candidate index:

```sql
CREATE INDEX perf_bookings_user_dates
ON bookings (user_id, check_in_date ASC, id DESC);
```

The candidate exists only in the disposable experiment database. The measured
optimization is now included as Liquibase migration 017 under the name
`idx_bookings_user_checkin_id`. For reproducible before/after runs, the experiment
drops that index only in its own temporary database before baseline measurement.
Compare median
`Execution Time`, plan nodes, returned rows, and buffer usage for the user's first
booking page. Also inspect count and accommodation-page plans as controls.

The SQL uses the repository's ordering with a concrete user filter and no status
filter. It does not reproduce every nullable-filter/prepared-plan combination.
Do not extrapolate its gain to deep OFFSET pages, status-filtered admin queries,
write throughput, or active-date overlap queries. Production index migration
requires assessing write/storage cost and migration locking as well as read benefit.
Migration 017 uses ordinary `CREATE INDEX`, which blocks writes while building.
For a large live database, plan a maintenance window or adapt rollout to a
non-transactional concurrent index build before applying it. The existing single
user index is retained because this experiment does not measure all query shapes.

## Recorded local run — 2026-10-02

Raw evidence: [read-performance-2026-10-02.json](read-performance-2026-10-02.json).
Java 21.0.8 on Windows 11, 16 available processors; PostgreSQL 17.9 in Docker;
Hikari maximum pool size 10. Each measured stage lasted approximately 15 seconds.

For the user's first booking page, median SQL execution time over five plans fell
from **0.340 ms to 0.034 ms**. The plan changed from bitmap heap scan plus top-N sort
to an ordered index scan; the last recorded plan touched **502 versus 23 shared
buffer blocks** and returned 20 rows in both cases. This supports the index for
this query shape and fixture, not a claim that the whole application is 10× faster.

| Clients | Phase | Combined req/s | Accommodation p95 ms | Booking history p95 ms | Errors |
| --- | --- | ---: | ---: | ---: | ---: |
| 5 | Baseline | 746 | 10.24 | 8.99 | 0 |
| 5 | Candidate index | 1208 | 5.42 | 4.27 | 0 |
| 20 | Baseline | 2022 | 12.56 | 12.03 | 0 |
| 20 | Candidate index | 2237 | 11.49 | 10.20 | 0 |
| 50 | Baseline | 1928 | 35.73 | 35.23 | 0 |
| 50 | Candidate index | 2136 | 32.25 | 30.95 | 0 |

These are observations from one baseline-then-candidate run. JIT/cache warming
and host effects explain some HTTP difference (including improvement in the
accommodation endpoint, which does not use the new index). At 50 clients,
throughput stopped growing and tail latency rose, but the bottleneck was not
identified and this run did not establish a failure threshold. The count plan
also changed to index-only scan; visibility-map/autovacuum effects can contribute.

Validation after adding timeout configuration and migration 017: `mvn verify`
passed with 277 successful tests and one intentionally skipped opt-in benchmark,
zero Checkstyle violations, and a passing JaCoCo coverage gate. The benchmark
itself was run separately and passed before the optimization became migration 017.

## HTTP failure boundaries

Stripe and Telegram default to 3-second connect and 10-second read timeouts,
configurable via `.env.sample`. Values outside 1–60000 ms fail startup validation.
Read timeouts bound inactivity rather than an overall deadline: a slow stream
that continues producing bytes can exceed the configured duration.

`HttpTimeoutTest` verifies delayed headers and incomplete bodies using a real
local HTTP server. Stripe performs one request without implicit SDK retry.
Telegram records failure, preserves retry behavior, and does not expose its token
or chat ID in the propagated exception. These tests do not simulate DNS delays,
TLS negotiation, or packet loss during TCP connection establishment.

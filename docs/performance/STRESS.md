# Mixed API stress and booking capacity

## Reproduce

JDK 21, Maven, and Docker are required. Only a disposable PostgreSQL 17 container
is used; the Compose database is untouched.

```powershell
mvn '-Dtest=BookingStressTest' '-Dstayhub.stress=true' test
```

Options:

- `-Dstayhub.stress.seconds=30`: measurement duration per stage, 1–300 seconds.
- `-Dstayhub.stress.rates=50,150,300,600,1200,2400,4800`: offered requests/second.
  Up to 20 stages, each rate 1–10000.
- `-Dstayhub.stress.max-in-flight=256`: maximum outstanding client requests, 1–2000.
- `-Dstayhub.stress.pool-size=10`: test-only Hikari maximum size, 1–80. The
  default minimum-idle value follows this size, so this experiment compares fixed
  pools with different connection budgets.
- `-Dstayhub.stress.warmup-seconds=3` and `-Dstayhub.stress.warmup-rate=100`:
  configurable warm-up duration and rate.
- `-Dstayhub.stress.report-name=booking-stress`: safe report basename; only letters,
  digits, underscores, and hyphens are accepted.

Normal CI skips this test. Reports are checkpointed after each stage to
`target/performance/booking-stress.json`; preserve them before `mvn clean`.

## Workload

The fixture has 200 JWT-authenticated customers, 5,000 accommodations, and
100,000 canceled history bookings. Each submitted request follows a deterministic
mix: 40% `GET /accommodations`, 40% `GET /bookings/my`, 20% `POST /bookings`.
Mixed writes rotate across 1,000 accommodations and use unique, nonoverlapping
one-night dates per accommodation; `409` is not expected in this workload.
Skipped slots may slightly change the submitted mix; operation counts are reported.

Each booking runs real service validation, PostgreSQL transactions and capacity
triggers, and transactional Outbox persistence. Only the background Outbox delivery
bean is mocked to isolate API/database stress. Kafka delivery, Stripe, Telegram,
BCrypt login, and network failures to those services are outside this experiment.
Application logging and JaCoCo remain enabled. JWTs are generated before measurement.

The generator schedules arrivals independently of responses, uses virtual threads,
and limits outstanding requests with a semaphore. It spins during the last 25 ms
before a scheduled slot to avoid coarse sleep timing on Windows. This consumes a
client CPU core on the same host as the application and affects observed capacity.
A slot more than five intervals late is skipped rather than replayed as a burst.
`generatorMissed` and `loadGeneratorLimited` distinguish host/generator limitations
from API errors. A stage with more than 1% missed slots does not establish capacity
at its nominal target rate; inspect the actual submitted rate instead.

Warm-up lasts three seconds at 100 offered requests/second. Its writes remain in
the database and integrity counters but are excluded from measured stage tables.
Later stages operate on the growing dataset; they are not identical fixed-data runs.

## Metrics and stop conditions

- Status histogram and transport exception class per operation. Status `0` means
  no HTTP response. Requests have a three-second client deadline; a timed-out write
  may still commit on the server and is treated as ambiguous during reconciliation.
- p50/p95/p99 from scheduled arrival through complete response-body consumption,
  including failures; also p95 measured from worker start. Nearest-rank quantiles.
- Offered slots, submitted requests, missed generator slots, and drops caused by
  the client in-flight limit. Submitted rate uses the configured arrival window;
  successful completion rate includes drain time.
- Hikari active/pending connection peaks sampled every 50 ms; these are sampled
  observations, not exact maxima or proof of the sole bottleneck.
- Mean sampled pending threads and per-stage mean connection acquisition/use
  times from Micrometer's Hikari timers, when available. Timer deltas exclude
  fixture/setup work and preceding stages. Means are not latency quantiles.
- The run stops at the first stage with more than 1% HTTP errors, more than 1%
  client-cap drops, or aggregate arrival p95 above 1000 ms. These are experiment
  guardrails, not production SLOs. Reaching the client cap is not a server rejection.

## Correctness and recovery

Before stress, 100 simultaneous requests target one accommodation with capacity 5
and identical dates. Exactly five must return 200 and persist, and 95 must return
409. The main write workload never intentionally exhausts capacity.

After all stages, the test waits up to 15 seconds for the database pool to drain,
checks Actuator health, and verifies:

- Committed mixed bookings are at least successful HTTP write responses and no
  more than submitted writes (allowing ambiguous timeout outcomes).
- Each committed booking, including the capacity race, has exactly one created
  Outbox event; there are no duplicate events for a booking.
- A sweep across grouped check-in/check-out boundaries finds no occupancy above
  accommodation capacity. Grouping boundaries preserves checkout exclusivity.

Overload metrics alone do not fail this exploratory stress test. Broken capacity,
Outbox reconciliation, health recovery, or inability to drain does fail it.

## Initial generator diagnostic

[Initial raw report](stress-generator-limited.json) shows why offered rate alone
is misleading: at a nominal 2400 requests/second only 3292 requests were submitted
in ten seconds, with 20708 missed slots and no Hikari waiters. That run validates
correctness but does not measure the API's capacity at 2400 requests/second.

## Recorded mixed run — 2026-10-03 (Europe/Warsaw)

[Raw report](booking-stress-2026-10-03.json). JDK 21.0.8, Windows 11, 16 available
processors, PostgreSQL 17.9 in Docker, default Hikari maximum pool size 10.
Each arrival window lasted ten seconds; generator and application shared the host.

| Offered req/s | Submitted req/s | Successful completion req/s including drain | Accommodation p95 ms | History p95 ms | Create p95 ms | Sampled peak Hikari waiters |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 50.0 | 50.0 | 9.03 | 7.39 | 13.02 | 0 |
| 150 | 150.0 | 150.0 | 7.28 | 5.81 | 10.55 | 0 |
| 300 | 300.0 | 299.9 | 6.18 | 5.08 | 9.14 | 0 |
| 600 | 599.5 | 599.2 | 6.27 | 5.24 | 9.35 | 0 |
| 1200 | 1198.7 | 1197.9 | 10.28 | 8.30 | 14.87 | 65 |
| 2400 | 1368.7 | 1353.4 | 372.40 | 364.09 | 409.37 | 190 |

All submitted measured requests returned 200. At the 2400 target, 9575 of 24000
offered slots were dropped by the client in-flight limit and 738 were missed by
the generator. The run stopped on its client-cap drop guardrail; the 4800 stage
was not attempted. This is an observed local pressure point, not proof of an
absolute API maximum or that 2400 requests/second were served.

At 1200, 11987 of 12000 requests were submitted with no cap drops, and the create
p95 remained 14.87 ms. At the next stage, the pool reached ten active connections,
waiters grew, and arrival p99 exceeded one second for every operation. Those
observations justify a controlled Hikari/SQL investigation; CPU, logging, JIT,
client work, and growing data can also contribute, so increasing the pool blindly
would not be a demonstrated fix.

Correctness and recovery results:

- Capacity race: five HTTP 200, 95 HTTP 409, exactly five committed bookings.
- 7406 mixed bookings committed, matching all 7406 successful write responses
  (including warm-up). Including the capacity race, 7411 created Outbox events.
- Zero duplicate created events and zero dates above accommodation capacity.
- Pool drained and Actuator health returned 200 after stress.

The opt-in stress test passed. It verifies safe state and recovery under its
bounded client pressure; it does not test unbounded queues, a remote generator,
Kafka drain throughput, provider outages, or a long-running soak workload.

After adding the stress test, `mvn verify` passed: 277 successful tests, two
intentionally skipped opt-in experiments, zero failures/errors, zero Checkstyle
violations, and a passing JaCoCo coverage gate.

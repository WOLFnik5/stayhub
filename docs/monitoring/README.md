# Local monitoring

The optional Compose overlay adds Prometheus, Grafana and Alertmanager to StayHub.
Prometheus scrapes the application every 15 seconds and retains seven days of data.
Grafana provisions the `StayHub Overview` dashboard and its datasource automatically.

## Start

Prepare the normal application `.env` as described in the project README, then run
from the repository root:

```powershell
./ops/monitoring/Initialize-Secrets.ps1
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up --build -d
```

The initializer creates random passwords only when their files do not already
exist. `ops/monitoring/secrets/` is ignored by Git. Docker mounts these files as
secrets; the application loads its scrape password through Spring's config tree.
Local Compose secrets are files on disk, so protect their filesystem access.

- Grafana: http://localhost:3000/d/stayhub-overview (user `admin`, password in
  `ops/monitoring/secrets/grafana-password`).
- Prometheus targets: http://localhost:9090/targets.
- Prometheus alerts: http://localhost:9090/alerts.
- Alertmanager: http://localhost:9093.

Monitoring UI ports bind to localhost. These tools are intended for local use;
the Prometheus and Alertmanager interfaces do not have authentication configured.
The existing application port follows the base Compose configuration.

The overlay enables `observability,monitoring`. Set `MONITORING_APP_PROFILES` to
a comma-separated list containing `monitoring` if additional profiles are needed.
Distributed tracing still requires the existing OTLP collector configuration.

## Metrics and access

Only `GET /actuator/prometheus` accepts the dedicated `stayhub-scraper` HTTP Basic
account. It cannot authenticate to business APIs or other Actuator endpoints.
Existing JWT authorization remains in effect. Without the `monitoring` profile,
the scrape endpoint is not exposed. A missing or short monitoring password prevents
startup; use at least 16 characters.

The dashboard contains 12 panels: HTTP throughput, p95 and 5xx ratio; Hikari active,
pending and maximum connections plus acquisition p95; process CPU and JVM heap;
Outbox counts, oldest age and snapshot age; booking event rates; and scrape health.
HTTP panels exclude health and Actuator requests. Histograms are enabled for HTTP,
Hikari acquisition and booking flow timers in the monitoring profile.

Rate panels need at least two scrapes. Quiet traffic, no connections acquired yet,
or no booking events can leave some panels without data. An empty event panel alone
does not indicate failure. Generate normal API traffic to inspect these panels.

## Alerts

| Rule | Condition | Sustained for |
| --- | --- | --- |
| Target down | Scrape fails or target is absent | 1 minute |
| HTTP errors | 5xx ratio > 5%, with traffic > 1 request/second | 2 minutes |
| HTTP latency | p95 > 1 second, with traffic > 1 request/second | 5 minutes |
| Hikari queue | Pending connection requests > 0 | 2 minutes |
| Outbox dead | DEAD events > 0 | 1 minute |
| Outbox lag | Oldest pending event > 120 seconds | 2 minutes |
| Stale snapshot | Outbox snapshot older than 90 seconds | 2 minutes |
| Dead letter | A consumer DLT event occurred within 5 minutes | Immediate |

Alerts appear in Prometheus and Alertmanager locally. The `local-only` receiver
has no email, chat or webhook integration, so it does not deliver notifications.
Thresholds are starting points for this local project, not production SLOs.

## Verify and stop

```powershell
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml config -q
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml exec prometheus promtool check config /etc/prometheus/prometheus.yml
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml exec prometheus promtool test rules /etc/prometheus/rules-test.yml
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml exec alertmanager amtool check-config /etc/alertmanager/alertmanager.yml
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml down
```

`down` retains named volumes. Grafana's administrator password is initialized on
its first startup; changing the secret file later does not reset the stored password.
For a 401 scrape error, check the password file, application profile and startup
logs, then recreate the application and Prometheus after changing credentials.

Automated security tests cover anonymous access, wrong credentials, valid scrape,
method restrictions, rejection of scrape credentials outside the endpoint, and
continued JWT/public-health access. Synthetic `promtool` tests cover firing delays,
all eight alert rules and healthy gauge values.

## References

See [recorded verification](VERIFICATION.md) for the tested stack and results.

- [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Alert rule testing](https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/)
- [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)

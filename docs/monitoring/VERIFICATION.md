# Verification: 2026-10-03

Executed against an isolated Compose project `stayhub-monitoring-check`, with
disposable PostgreSQL/Kafka volumes, dummy provider credentials and alternate
localhost ports. The normal application environment was not used for this run.

- `mvn verify`: 285 tests total, 283 passed, two opt-in performance tests skipped;
  zero failures/errors. Checkstyle and JaCoCo coverage gate passed.
- Prometheus `promtool check config`: configuration and all eight alert rules valid.
- Prometheus `promtool test rules`: all synthetic rule tests passed.
- Alertmanager `amtool check-config`: configuration valid.
- Prometheus target: `up`, no scrape error, using the protected endpoint and
  the password mounted through Compose secrets/config tree.
- Grafana health: database `ok`; provisioned datasource health `OK`;
  dashboard `stayhub-overview` contained all 12 panels.
- All 14 panel expressions executed successfully in Prometheus. Every expression
  returned data except async event rate, which had no events in this read-only run.
- Stopping only the isolated application produced `StayHubTargetDown` in Prometheus
  and an active alert in Alertmanager. Restarting restored successful scraping;
  the alert cleared in Prometheus and Alertmanager. The isolated stack and its
  volumes were removed after verification.

Versions tested: Prometheus 3.13.4, Alertmanager 0.34.1, Grafana 13.2.3.
Grafana provisioning and queries were checked through APIs; browser rendering was
not visually inspected. Alerts have a local-only receiver and send no notifications.

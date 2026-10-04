# Compose network access

The base Compose configuration is intended for local development. All
published ports bind to `127.0.0.1`:

| Service | Host address | Compose network address |
| --- | --- | --- |
| API | `127.0.0.1:8080` | `booking-app:8080` |
| PostgreSQL | `127.0.0.1:5433` | `postgres:5432` |
| Kafka for host clients | `127.0.0.1:9092` | — |
| Kafka for containers | Not published | `kafka:29092` |
| Kafka UI, `tools` profile | `127.0.0.1:8081` | `kafka-ui:8080` |
| Jaeger, `tracing` profile | `127.0.0.1:16686`, `127.0.0.1:4318` | `jaeger:16686`, `jaeger:4318` |

Kafka UI does not start with a regular `docker compose up`. To start it:

```bash
docker compose --profile tools up -d kafka-ui
```

It has no separate authentication and uses the internal Kafka listener.
Do not expose it to an untrusted network. Explicitly starting a service by name
also activates its profile; see
[Compose profile behavior](https://docs.docker.com/compose/how-tos/profiles/).

The monitoring overlay retains loopback bindings for Prometheus `9090`,
Alertmanager `9093`, and Grafana `3000`. The deployment overlay removes
PostgreSQL and Kafka port publications; the API remains on `127.0.0.1:8080`.
Communication between containers and the database, broker, or UI does not
require publishing these ports on the host.

## Applying changes to running containers

Editing YAML alone does not change existing port bindings. During a planned
maintenance window, recreate services through Compose while retaining volumes:

```bash
docker compose up -d --no-deps postgres kafka
# Stop the UI if it was running previously and is no longer needed:
docker compose --profile tools stop kafka-ui
# Alternatively, recreate it with the new loopback binding:
docker compose --profile tools up -d --no-deps kafka-ui
```

The API receives its new binding during a normal startup or deployment.
For an existing database, first complete the [role transition](../database-roles.md)
if it has not already been done. Do not use `down -v`: changing network bindings
does not require deleting data.

## Remote access

Use a configured HTTPS reverse proxy on the host for external API access.
An SSH tunnel can provide remote access to the administration UI:

```bash
ssh -N -L 8081:127.0.0.1:8081 user@server
```

Then open `http://127.0.0.1:8081` on your computer. Kafka listeners in this local
stack remain PLAINTEXT. Loopback bindings do not add TLS/SASL or establish trust
boundaries between containers on the same network. An external broker requires
separate authentication, TLS, and ACL configuration.

Use Docker Engine 28 or newer: Docker documents that older releases may allow
hosts on the same L2 segment to access localhost port publications. Also check
the firewall and any custom daemon/direct-routing settings on the actual host.
See the [port publishing documentation](https://docs.docker.com/engine/network/port-publishing/).

## Configuration verification

```bash
python3 ops/deployment/check-compose.py
```

The script checks the effective Compose configuration for the base stack,
monitoring, deployment, and their combination, as well as service selection
through profiles. It uses only test credentials, does not read `.env`, and does
not start containers. This check runs in CI; it does not replace network testing
on the actual host.

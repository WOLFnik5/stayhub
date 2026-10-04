"""Check effective Compose network exposure without reading .env or starting services."""

import json
import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def config(env_file, environment, overlays=(), profiles=(), services_only=False):
    command = [
        "docker", "compose", "--project-directory", str(ROOT),
        "--project-name", "stayhub-compose-check", "--env-file", str(env_file),
    ]
    for profile in profiles:
        command += ["--profile", profile]
    for filename in ("docker-compose.yml", *overlays):
        command += ["-f", str(ROOT / filename)]
    command += ["config", "--no-env-resolution"]
    command += ["--services"] if services_only else ["--format", "json"]
    result = subprocess.run(command, env=environment, capture_output=True,
                            text=True, check=False, timeout=30)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise RuntimeError(
            f"Compose config failed for {', '.join(overlays) or 'local'} "
            f"(exit {result.returncode}): {detail}"
        )
    return set(result.stdout.splitlines()) if services_only else json.loads(result.stdout)


def verify_ports(model, deployed):
    services = model["services"]
    for name, service in services.items():
        for port in service.get("ports", []):
            check(port.get("host_ip") == "127.0.0.1",
                  f"{name}: published port must bind to IPv4 loopback")
    expected = [] if deployed else [(9092, "9092")]
    actual = [(port["target"], str(port["published"]))
              for port in services["kafka"].get("ports", [])]
    check(actual == expected, "Kafka internal/controller listeners must not be published")
    if deployed:
        check(not services["postgres"].get("ports"), "Deployment must not publish PostgreSQL")
    check(services["kafka-ui"].get("profiles") == ["tools"],
          "Kafka UI must require the tools profile")


def main():
    # Override interpolated variables with test values. --no-env-resolution keeps
    # application env_file contents (including a developer's real .env) unread.
    # Compose still requires the referenced .env file to exist, so create an
    # empty one only when absent and remove it after the check.
    with tempfile.TemporaryDirectory(prefix="stayhub-compose-check-") as directory:
        env_file = Path(directory) / "test.env"
        env_file.write_text("", encoding="utf-8")
        environment = os.environ | {
            "COMPOSE_PROFILES": "", "SPRING_PROFILES_ACTIVE": "observability",
            "POSTGRES_DB": "booking_test", "POSTGRES_ADMIN_USER": "booking_admin",
            "POSTGRES_ADMIN_PASSWORD": "test_admin_password",
            "DB_USERNAME": "booking_runtime", "DB_PASSWORD": "test_runtime_password",
            "LIQUIBASE_USERNAME": "booking_migrator",
            "LIQUIBASE_PASSWORD": "test_migration_password",
            "KAFKA_CLUSTER_ID": "MkU3OEVBNTcwNTJENDM2Qk",
            "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092",
            "KAFKA_CONTAINER_BOOTSTRAP_SERVERS": "kafka:29092",
            "STAYHUB_IMAGE": "ghcr.io/test/stayhub@sha256:" + "a" * 64,
            "STAYHUB_ENV_FILE": str(env_file),
        }
        placeholder_env = ROOT / ".env"
        created_placeholder = False
        try:
            try:
                with placeholder_env.open("x", encoding="utf-8"):
                    pass
                created_placeholder = True
            except FileExistsError:
                pass

            default_services = config(env_file, environment, services_only=True)
            check(default_services == {"postgres", "kafka", "booking-migrate", "booking-app"},
                  "Default startup must exclude optional administration and tracing services")
            tools_services = config(env_file, environment, profiles=("tools",), services_only=True)
            check("kafka-ui" in tools_services, "tools profile must enable Kafka UI")
            for label, overlays, deployed in (
                ("local", (), False),
                ("monitoring", ("docker-compose.monitoring.yml",), False),
                ("deployment", ("docker-compose.deploy.yml",), True),
                ("deployment + monitoring",
                 ("docker-compose.deploy.yml", "docker-compose.monitoring.yml"), True),
            ):
                model = config(env_file, environment, overlays, profiles=("tools", "tracing"))
                verify_ports(model, deployed)
                check(model["services"]["booking-app"]["environment"]["KAFKA_BOOTSTRAP_SERVERS"]
                      == "kafka:29092", "API container must use the internal Kafka listener")
                print(f"PASS: {label} effective port bindings and optional tools")
            print("PASS: default service selection and explicit tools profile")
        finally:
            if created_placeholder:
                placeholder_env.unlink(missing_ok=True)


if __name__ == "__main__":
    main()

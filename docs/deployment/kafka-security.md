# Kafka client configuration

The producer, Telegram consumer, and Boot Kafka Admin use standard
`spring.kafka` properties. Shared `spring.kafka.properties.*` settings reach
both factories; `spring.kafka.producer.properties.*` and
`spring.kafka.consumer.properties.*` can override settings for their respective
clients. This includes TLS/SASL, request/delivery timeouts, compression, acks,
and consumer poll settings. See the
[KafkaProperties contract](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/kafka/autoconfigure/KafkaProperties.html).

Application events retain their text format: key/value serializers and
deserializers always use String. The Telegram group is
`${spring.kafka.consumer.group-id}-telegram`; configure it through
`spring.kafka.consumer.group-id`, rather than raw `group.id`. Snappy is used
only when no compression setting is supplied. `spring.kafka.listener.auto-startup`
controls Telegram listener startup; application retry/DLT and tracing remain
in place.

## Local stack addresses

For an API running in your IDE, use `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.
The API container receives `KAFKA_CONTAINER_BOOTSTRAP_SERVERS`, defaulting to
`kafka:29092`. The host process's setting therefore does not replace the
container broker address with `localhost`. For an external broker, explicitly
set the container variable to its addresses.

## External SASL_SSL broker

The local Compose broker remains PLAINTEXT. For an existing broker configured
with SASL/PLAIN over TLS, supply an additional Spring configuration file, for
example:

```yaml
spring:
  kafka:
    security:
      protocol: SASL_SSL
    ssl:
      trust-store-location: file:/run/secrets/kafka-truststore.p12
      trust-store-password: ${KAFKA_TRUSTSTORE_PASSWORD}
      trust-store-type: PKCS12
    properties:
      "[sasl.mechanism]": PLAIN
      "[sasl.jaas.config]": ${KAFKA_SASL_JAAS_CONFIG}
      "[ssl.endpoint.identification.algorithm]": https
    producer:
      acks: all
      properties:
        "[delivery.timeout.ms]": 30000
        "[request.timeout.ms]": 10000
    consumer:
      max-poll-records: 100
```

Mount the configuration file and truststore read-only in the API container.
Add `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/run/config/kafka-client.yml` to its
environment through your own Compose override. Supply JAAS configuration and
the truststore password through a secret manager or private process
environment; do not put real secrets in tracked YAML or README files.
For PLAIN, the JAAS value has this form:
`org.apache.kafka.common.security.plain.PlainLoginModule required username="..." password="...";`.

The SASL mechanism, certificates, credentials, and broker ACLs must match the
actual broker. Do not disable hostname verification to bypass certificate
errors. The producer/consumer factories support file-based truststore/keystore
properties; Spring SSL bundles are not separately integrated into these custom
factories. This example configures clients; it does not deploy a secure cluster.

## Verification

`KafkaClientConfigurationTest` verifies Spring binding, the factories' actual
configuration maps, client overrides, wire format, and disabled listener startup.
`KafkaSecureConnectionIntegrationTest` creates a temporary certificate and a
Docker Kafka broker with SASL_SSL, tests producer-to-consumer delivery, and
verifies that incorrect passwords and untrusted certificates are rejected.
It uses no real secrets or data and stops its container after the tests.

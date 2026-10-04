package com.bookingapp.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.infrastructure.config.KafkaConsumerConfiguration;
import com.bookingapp.infrastructure.config.KafkaProducerConfiguration;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

class KafkaSecureConnectionIntegrationTest {
    private static final String PASSWORD = "temporary-test-password";
    private static final String TOPIC = "secure-round-trip";
    @TempDir
    private static Path directory;
    private static GenericContainer<?> broker;
    private static Path store;

    @BeforeAll
    static void startBroker() throws Exception {
        store = directory.resolve("broker.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows")
                        ? "keytool.exe" : "keytool").toString();
        Process certificate = new ProcessBuilder(keytool, "-genkeypair", "-alias", "broker",
                "-keystore", store.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD,
                "-keypass", PASSWORD, "-keyalg", "RSA", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile())
                .start();
        assertThat(certificate.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(certificate.exitValue()).isZero();
        Files.writeString(directory.resolve("store-password"), PASSWORD);
        broker = new SecureBroker();
        broker.withExposedPorts(9093)
                .withEnv("KAFKA_NODE_ID", "1")
                .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:29093")
                .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL")
                .withEnv("KAFKA_LISTENERS", "INTERNAL://0.0.0.0:29092,"
                        + "EXTERNALSSL://0.0.0.0:9093,CONTROLLER://0.0.0.0:29093")
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                        "INTERNAL:PLAINTEXT,EXTERNALSSL:SASL_SSL,CONTROLLER:PLAINTEXT")
                .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAIN")
                .withEnv("KAFKA_LISTENER_NAME_EXTERNALSSL_PLAIN_SASL_JAAS_CONFIG",
                        "org.apache.kafka.common.security.plain.PlainLoginModule required "
                                + "user_booking=\"" + PASSWORD + "\";")
                .withEnv("KAFKA_SSL_KEYSTORE_FILENAME", "broker.p12")
                .withEnv("KAFKA_SSL_KEYSTORE_CREDENTIALS", "store-password")
                .withEnv("KAFKA_SSL_KEY_CREDENTIALS", "store-password")
                .withEnv("KAFKA_SSL_TRUSTSTORE_FILENAME", "broker.p12")
                .withEnv("KAFKA_SSL_TRUSTSTORE_CREDENTIALS", "store-password")
                .withEnv("KAFKA_SSL_KEYSTORE_TYPE", "PKCS12")
                .withEnv("KAFKA_SSL_TRUSTSTORE_TYPE", "PKCS12")
                .withEnv("KAFKA_SSL_CLIENT_AUTH", "none")
                .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
                .withCopyFileToContainer(MountableFile.forHostPath(store),
                        "/etc/kafka/secrets/broker.p12")
                .withCopyFileToContainer(MountableFile.forHostPath(directory.resolve("store-password")),
                        "/etc/kafka/secrets/store-password")
                .withCommand("bash", "-c", "while [ ! -f /tmp/start-secure-kafka.sh ]; "
                        + "do sleep 0.1; done; exec /tmp/start-secure-kafka.sh")
                .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
        broker.start();
        try (AdminClient admin = AdminClient.create(properties().buildAdminProperties())) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        }
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (broker != null) {
            try {
                Files.writeString(Path.of("target/kafka-secure-broker.log"), broker.getLogs());
            } finally {
                broker.stop();
            }
        }
    }

    @Test
    void applicationFactoriesDeliverThroughSaslSslWithCertificateValidation() throws Exception {
        var producerFactory = (DefaultKafkaProducerFactory<String, String>)
                new KafkaProducerConfiguration().producerFactory(properties());
        var consumerFactory = new KafkaConsumerConfiguration().telegramConsumerFactory(properties());
        try (var producer = producerFactory.createProducer();
                var consumer = consumerFactory.createConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            producer.send(new ProducerRecord<>(TOPIC, "key", "secure-event"))
                    .get(20, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            boolean received = false;
            while (!received && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if ("secure-event".equals(record.value())) {
                        received = true;
                    }
                }
            }
            assertThat(received).isTrue();
        } finally {
            producerFactory.destroy();
        }
    }

    @Test
    void incorrectSaslCredentialsAreRejected() {
        KafkaProperties properties = properties();
        properties.getProperties().put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required "
                        + "username=\"booking\" password=\"incorrect\";");
        assertRejected(properties, SaslAuthenticationException.class);
    }

    @Test
    void untrustedCertificateIsRejected() {
        KafkaProperties properties = properties();
        properties.getProperties().remove("ssl.truststore.location");
        properties.getProperties().remove("ssl.truststore.password");
        properties.getProperties().remove("ssl.truststore.type");
        assertRejected(properties, SslAuthenticationException.class);
    }

    private void assertRejected(KafkaProperties properties, Class<? extends Throwable> failure) {
        var factory = (DefaultKafkaProducerFactory<String, String>)
                new KafkaProducerConfiguration().producerFactory(properties);
        try (var producer = factory.createProducer()) {
            assertThatThrownBy(() -> producer.send(new ProducerRecord<>(TOPIC, "rejected"))
                    .get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(failure);
        } finally {
            factory.destroy();
        }
    }

    private static KafkaProperties properties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of(broker.getHost() + ":" + broker.getMappedPort(9093)));
        properties.getConsumer().setGroupId("secure-test-" + UUID.randomUUID());
        properties.getConsumer().setAutoOffsetReset("earliest");
        properties.getSecurity().setProtocol("SASL_SSL");
        properties.getProperties().put("sasl.mechanism", "PLAIN");
        properties.getProperties().put("sasl.jaas.config",
                "org.apache.kafka.common.security.plain.PlainLoginModule required "
                        + "username=\"booking\" password=\"" + PASSWORD + "\";");
        properties.getProperties().put("ssl.truststore.location", store.toString());
        properties.getProperties().put("ssl.truststore.password", PASSWORD);
        properties.getProperties().put("ssl.truststore.type", "PKCS12");
        properties.getProperties().put("ssl.endpoint.identification.algorithm", "https");
        properties.getProperties().put("request.timeout.ms", "5000");
        properties.getProducer().getProperties().put("delivery.timeout.ms", "10000");
        return properties;
    }

    private static final class SecureBroker extends GenericContainer<SecureBroker> {
        private SecureBroker() {
            super("confluentinc/cp-kafka:7.8.7");
        }

        @Override
        protected void containerIsStarting(InspectContainerResponse containerInfo) {
            String start = "#!/bin/bash\nexport KAFKA_ADVERTISED_LISTENERS="
                    + "'INTERNAL://localhost:29092,EXTERNALSSL://" + getHost() + ":"
                    + getMappedPort(9093) + "'\nexec /etc/confluent/docker/run\n";
            copyFileToContainer(Transferable.of(start, 0755), "/tmp/start-secure-kafka.sh");
        }
    }
}

package com.bookingapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.observability.TelegramRecordInterceptor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;

class KafkaClientConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaProducerConfiguration.class, KafkaConsumerConfiguration.class)
            .withBean(TelegramRecordInterceptor.class, () -> mock(TelegramRecordInterceptor.class))
            .withBean(FlowTelemetry.class, () -> mock(FlowTelemetry.class))
            .withBean(FlowTracing.class, () -> mock(FlowTracing.class))
            .withPropertyValues(
                    "app.kafka.topics.booking-created=booking.created",
                    "app.kafka.topics.booking-canceled=booking.canceled",
                    "app.kafka.topics.booking-expired=booking.expired",
                    "app.kafka.topics.accommodation-created=accommodation.created",
                    "app.kafka.topics.payment-succeeded=payment.succeeded");

    @Test
    void securityAndCommonPropertiesReachBothFactories() {
        runner.withPropertyValues(
                "spring.kafka.bootstrap-servers=first:9093,second:9093",
                "spring.kafka.security.protocol=SASL_SSL",
                "spring.kafka.properties[sasl.mechanism]=PLAIN",
                "spring.kafka.properties[sasl.jaas.config]=test-login-module required;",
                "spring.kafka.ssl.trust-store-location=file:/test/truststore.p12",
                "spring.kafka.ssl.trust-store-password=test-only-password",
                "spring.kafka.ssl.trust-store-type=PKCS12",
                "spring.kafka.properties[request.timeout.ms]=12000",
                "spring.kafka.consumer.group-id=custom",
                "spring.kafka.listener.auto-startup=false"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var producer = context.getBean(ProducerFactory.class).getConfigurationProperties();
            var consumer = context.getBean(ConsumerFactory.class).getConfigurationProperties();
            for (var properties : java.util.List.of(producer, consumer)) {
                assertThat(properties).containsEntry("security.protocol", "SASL_SSL")
                        .containsEntry("sasl.mechanism", "PLAIN")
                        .containsEntry("sasl.jaas.config", "test-login-module required;")
                        .containsEntry("ssl.truststore.password", "test-only-password")
                        .containsEntry("ssl.truststore.type", "PKCS12")
                        .containsEntry("request.timeout.ms", "12000");
                assertThat(properties.get("ssl.truststore.location").toString())
                        .endsWith("truststore.p12");
                assertThat(properties.get("bootstrap.servers"))
                        .isEqualTo(java.util.List.of("first:9093", "second:9093"));
            }
            assertThat(consumer).containsEntry("group.id", "custom-telegram");
            assertThat(context.getBean("telegramKafkaListenerContainerFactory",
                    ConcurrentKafkaListenerContainerFactory.class)
                    .createContainer("test-topic").isAutoStartup()).isFalse();
        });
    }

    @Test
    void clientSpecificOverridesAndApplicationWireFormatArePreserved() {
        runner.withPropertyValues(
                "spring.kafka.properties[request.timeout.ms]=12000",
                "spring.kafka.producer.properties[request.timeout.ms]=7000",
                "spring.kafka.consumer.properties[request.timeout.ms]=9000",
                "spring.kafka.producer.compression-type=gzip",
                "spring.kafka.producer.acks=all",
                "spring.kafka.consumer.max-poll-records=20",
                "spring.kafka.consumer.auto-offset-reset=latest",
                "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer",
                "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var producer = context.getBean(ProducerFactory.class).getConfigurationProperties();
            var consumer = context.getBean(ConsumerFactory.class).getConfigurationProperties();
            assertThat(producer).containsEntry("request.timeout.ms", "7000")
                    .containsEntry("compression.type", "gzip")
                    .containsEntry("acks", "all")
                    .containsEntry("value.serializer", StringSerializer.class);
            assertThat(consumer).containsEntry("request.timeout.ms", "9000")
                    .containsEntry("max.poll.records", 20)
                    .containsEntry("auto.offset.reset", "latest")
                    .containsEntry("value.deserializer", StringDeserializer.class);
        });
    }

    @Test
    void defaultCompressionAndConsumerSettingsRemainCompatible() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProducerFactory.class).getConfigurationProperties())
                    .containsEntry("compression.type", "snappy");
            assertThat(context.getBean(ConsumerFactory.class).getConfigurationProperties())
                    .containsEntry("auto.offset.reset", "earliest")
                    .containsEntry("group.id", "booking-app-telegram");
        });
    }
}

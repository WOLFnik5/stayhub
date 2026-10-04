package com.bookingapp.infrastructure.config;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
@EnableConfigurationProperties({KafkaTopicsProperties.class, KafkaProperties.class})
public class KafkaProducerConfiguration {

    @Bean
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties
    ) {
        Map<String, Object> properties = kafkaProperties.buildProducerProperties();
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.putIfAbsent(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String,
            String> producerFactory) {
        // OutboxKafkaPublisher owns the PRODUCER span and injects its durable-parent context.
        return new KafkaTemplate<>(producerFactory);
    }
}

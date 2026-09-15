package com.bookingapp.infrastructure.config;

import com.bookingapp.infrastructure.observability.CorrelationContext;
import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.observability.TelegramRecordInterceptor;
import io.opentelemetry.api.trace.SpanKind;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConsumerConfiguration {

    @Bean
    public ConsumerFactory<String, String> telegramConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:booking-app}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:earliest}") String autoOffsetReset
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-telegram");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,
            String> telegramKafkaListenerContainerFactory(
            ConsumerFactory<String, String> telegramConsumerFactory,
            TelegramRecordInterceptor interceptor, FlowTelemetry telemetry, FlowTracing tracing
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(telegramConsumerFactory);
        // The interceptor owns one CONSUMER span per delivery, including retries.
        factory.setRecordInterceptor(interceptor);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            try (CorrelationContext ignored = TelegramRecordInterceptor.openContext(record);
                    var trace = tracing.resume("telegram.exhausted", SpanKind.INTERNAL,
                            TelegramRecordInterceptor.header(record, "traceparent"),
                            TelegramRecordInterceptor.header(record, "tracestate"))) {
                trace.error("RetriesExhausted");
                trace.tag("messaging.destination.name", record.topic());
                trace.tag("messaging.kafka.offset", record.offset());
                telemetry.count("consumer", "exhausted", 1);
                telemetry.record("consumer", "exhausted", System.nanoTime(), "RetriesExhausted");
            }
        });
        handler.setRetryListeners((RetryListener) (record, exception, attempt) -> {
            if (attempt > 1) {
                try (CorrelationContext ignored = TelegramRecordInterceptor.openContext(record)) {
                    MDC.put("attempt", Integer.toString(attempt));
                    telemetry.count("consumer", "retry", 1);
                }
            }
        });
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}

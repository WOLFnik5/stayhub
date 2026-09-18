package com.bookingapp.infrastructure.config;

import com.bookingapp.infrastructure.observability.CorrelationContext;
import com.bookingapp.infrastructure.observability.FlowTelemetry;
import com.bookingapp.infrastructure.observability.FlowTracing;
import com.bookingapp.infrastructure.observability.TelegramRecordInterceptor;
import io.opentelemetry.api.trace.SpanKind;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

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
            TelegramRecordInterceptor interceptor, FlowTelemetry telemetry, FlowTracing tracing,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.consumer.retry.max-attempts:4}") int maxAttempts,
            @Value("${app.kafka.consumer.retry.backoff-ms:1000}") long retryBackoffMs,
            @Value("${app.kafka.consumer.dlt-suffix:.DLT}") String deadLetterSuffix
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(telegramConsumerFactory);
        // The interceptor owns one CONSUMER span per delivery, including retries.
        factory.setRecordInterceptor(interceptor);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + deadLetterSuffix,
                        record.partition())
        );
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            recoverer.accept(record, exception);
            try (CorrelationContext ignored = TelegramRecordInterceptor.openContext(record);
                    var trace = tracing.resume("telegram.dead_letter", SpanKind.PRODUCER,
                            TelegramRecordInterceptor.header(record, "traceparent"),
                            TelegramRecordInterceptor.header(record, "tracestate"))) {
                trace.error("RetriesExhausted");
                trace.tag("messaging.destination.name", record.topic() + deadLetterSuffix);
                trace.tag("messaging.kafka.offset", record.offset());
                telemetry.count("consumer", "exhausted", 1);
                telemetry.count("consumer", "dead_letter", 1);
                telemetry.record("consumer", "exhausted", System.nanoTime(), "RetriesExhausted");
            }
        }, new FixedBackOff(retryBackoffMs, Math.max(0, maxAttempts - 1L)));
        handler.setCommitRecovered(true);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
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

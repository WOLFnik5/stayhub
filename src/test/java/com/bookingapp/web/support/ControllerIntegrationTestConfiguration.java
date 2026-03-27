package com.bookingapp.web.support;

import com.bookingapp.infrastructure.kafka.KafkaEventPublisher;
import com.bookingapp.infrastructure.stripe.StripePaymentProvider;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ControllerIntegrationTestConfiguration {

    @Bean
    @Primary
    KafkaEventPublisher kafkaEventPublisher() {
        return Mockito.mock(KafkaEventPublisher.class);
    }

    @Bean
    @Primary
    StripePaymentProvider stripePaymentProvider() {
        return Mockito.mock(StripePaymentProvider.class);
    }
}

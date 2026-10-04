package com.bookingapp.infrastructure.config;

import com.stripe.StripeClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfiguration {

    @Bean
    public StripeClient stripeClient(StripeProperties stripeProperties) {
        return StripeClient.builder()
                .setApiKey(stripeProperties.getSecretKey())
                .setConnectTimeout(stripeProperties.getConnectTimeoutMs())
                .setReadTimeout(stripeProperties.getReadTimeoutMs())
                // Durable payment attempts own retries; avoid multiplying HTTP latency here.
                .setMaxNetworkRetries(0)
                .build();
    }
}

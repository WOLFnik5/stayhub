package com.bookingapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class MonitoringPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void missingOrShortPasswordFailsValidation() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.monitoring.password=short")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitlyConfiguredPasswordPassesValidation() {
        runner.withPropertyValues("app.monitoring.password=local-test-scrape-password")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MonitoringProperties.class)
    static class PropertiesConfiguration {
    }
}

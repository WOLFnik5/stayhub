package com.bookingapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class RuntimeConfigurationTest {

    @Test
    void ordinaryProfileDoesNotEnableDevDiagnostics() throws IOException {
        var environment = new StandardEnvironment();
        load(environment, "application.yml");

        assertThat(environment.getProperty("spring.profiles.default")).isNull();
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("WARN");
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind"))
                .isEqualTo("WARN");
    }

    @Test
    void devProfileKeepsExplicitSqlDiagnostics() throws IOException {
        var environment = new StandardEnvironment();
        load(environment, "application.yml");
        load(environment, "application-dev.yml");

        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isTrue();
        assertThat(environment.getProperty("logging.level.org.hibernate.SQL")).isEqualTo("DEBUG");
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind"))
                .isEqualTo("TRACE");
    }

    @Test
    void missingIntegrationCredentialsFailStartupValidation() throws IOException {
        propertiesRunner().run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "app.stripe.secret-key=sk_test_replace_me",
        "app.telegram.bot-token=replace-with-bot-token",
        "app.telegram.chat-id=replace-with-chat-id"
    })
    void knownTemplateCredentialsFailStartupValidation(String placeholder) throws IOException {
        propertiesRunner()
                .withPropertyValues(validProperties())
                .withPropertyValues(placeholder)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitlyConfiguredIntegrationCredentialsPassValidation() throws IOException {
        propertiesRunner()
                .withPropertyValues(validProperties())
                .run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner propertiesRunner() throws IOException {
        var sources = new YamlPropertySourceLoader().load(
                "application", new ClassPathResource("application.yml"));
        return new ApplicationContextRunner()
                .withUserConfiguration(IntegrationPropertiesConfiguration.class)
                .withInitializer(context -> {
                    var propertySources = context.getEnvironment().getPropertySources();
                    propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.forEach(propertySources::addLast);
                });
    }

    private void load(StandardEnvironment environment, String resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                resource, new ClassPathResource(resource));
        sources.forEach(environment.getPropertySources()::addFirst);
    }

    private String[] validProperties() {
        return new String[] {
            "app.stripe.secret-key=sk_test_local_value",
            "app.stripe.webhook-secret=whsec_local_value",
            "app.stripe.success-url=http://localhost/success",
            "app.stripe.cancel-url=http://localhost/cancel",
            "app.telegram.bot-token=local-bot-token",
            "app.telegram.chat-id=local-chat-id"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({StripeProperties.class, TelegramProperties.class})
    static class IntegrationPropertiesConfiguration {
    }
}

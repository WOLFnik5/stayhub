package com.bookingapp.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.infrastructure.config.JwtProperties;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class JwtTokenServiceTest {

    // Load the real application YAML so a reintroduced default key fails these tests.
    private ApplicationContextRunner contextRunner() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application",
                new ClassPathResource("application.yml"));
        return new ApplicationContextRunner()
                .withUserConfiguration(JwtConfiguration.class)
                .withInitializer(context -> {
                    var properties = context.getEnvironment().getPropertySources();
                    properties.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    properties.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.forEach(properties::addLast);
                });
    }

    @Test
    void shouldRefuseStartupWithoutSecret() throws IOException {
        contextRunner().run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "replace-with-a-strong-base64-secret", "c2hvcnQ="})
    void shouldRefuseStartupWithInvalidSecret(String secret) throws IOException {
        contextRunner().withPropertyValues("JWT_SECRET=" + secret)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 64})
    void shouldStartAndVerifyTokensWithConfiguredKey(int bytes) throws IOException {
        contextRunner().withPropertyValues("JWT_SECRET=" + randomKey(bytes))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtTokenService service = context.getBean(JwtTokenService.class);
                    User user = new User(17L, "customer@example.com", "Test", "Customer",
                            "unused-test-hash", UserRole.CUSTOMER);
                    assertThat(service.parsePrincipal(service.generateToken(user)))
                            .isEqualTo(new AuthenticatedUserPrincipal(
                                    user.getId(), user.getEmail(), user.getRole()));
                });
    }

    @Test
    void shouldRejectAdminTokenSignedWithAnotherKey() throws IOException {
        JwtProperties attackerProperties = new JwtProperties();
        attackerProperties.setSecret(randomKey(32));
        attackerProperties.setExpirationMinutes(60);
        String forgedToken = new JwtTokenService(attackerProperties).generateToken(
                new User(1L, "admin@example.com", "Test", "Admin",
                        "unused-test-hash", UserRole.ADMIN));

        contextRunner().withPropertyValues("JWT_SECRET=" + randomKey(32))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getBean(JwtTokenService.class)
                            .parsePrincipal(forgedToken)).isInstanceOf(SignatureException.class);
                });
    }

    private String randomKey(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtTokenService.class)
    static class JwtConfiguration {
    }
}

package com.bookingapp.infrastructure.security;

import com.bookingapp.infrastructure.config.MonitoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("monitoring")
@EnableConfigurationProperties(MonitoringProperties.class)
public class MonitoringSecurityConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain prometheusSecurityFilterChain(HttpSecurity http,
            MonitoringProperties properties, PasswordEncoder encoder) throws Exception {
        var reader = User.withUsername(properties.getUsername())
                .password(encoder.encode(properties.getPassword())).roles("METRICS").build();
        var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(reader));
        provider.setPasswordEncoder(encoder);
        http.securityMatcher("/actuator/prometheus")
                .authenticationProvider(provider)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("METRICS")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }
}

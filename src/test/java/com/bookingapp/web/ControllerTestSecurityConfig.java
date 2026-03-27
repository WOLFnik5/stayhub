package com.bookingapp.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
public class ControllerTestSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/payments/success", "/payments/cancel").permitAll()
                        .requestMatchers(HttpMethod.GET, "/accommodations", "/accommodations/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/accommodations").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/accommodations/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/accommodations/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/accommodations/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/users/*/role").hasRole("ADMIN")
                        .requestMatchers("/users/me").authenticated()
                        .requestMatchers("/bookings/**").authenticated()
                        .requestMatchers("/payments/**").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}

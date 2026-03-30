package com.bookingapp;

import com.bookingapp.infrastructure.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
@SpringBootApplication
public class BookingAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingAppApplication.class, args);
    }
}

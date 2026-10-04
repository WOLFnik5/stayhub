package com.bookingapp.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {
    @NotBlank
    private String username = "stayhub-scraper";

    @NotBlank
    @Size(min = 16, max = 256)
    private String password;
}

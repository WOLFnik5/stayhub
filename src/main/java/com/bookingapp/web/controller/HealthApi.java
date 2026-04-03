package com.bookingapp.web.controller;

import com.bookingapp.web.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Health", description = "Application health endpoints")
public interface HealthApi {

    @GetMapping("/health")
    @Operation(summary = "Custom health check")
    HealthResponse health();
}

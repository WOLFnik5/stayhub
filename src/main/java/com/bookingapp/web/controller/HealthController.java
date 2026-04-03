package com.bookingapp.web.controller;

import com.bookingapp.web.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health", description = "Application health endpoints")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Custom health check")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}

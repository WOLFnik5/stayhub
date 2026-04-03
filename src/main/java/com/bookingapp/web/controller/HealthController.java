package com.bookingapp.web.controller;

import com.bookingapp.web.dto.HealthResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements HealthApi {

    @Override
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}

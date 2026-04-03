package com.bookingapp.web.controller;

import com.bookingapp.web.dto.AuthResponse;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration and login endpoints")
public interface AuthApi {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    AuthResponse register(@Valid @RequestBody RegisterRequest request);

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT bearer token")
    AuthResponse login(@Valid @RequestBody LoginRequest request);
}

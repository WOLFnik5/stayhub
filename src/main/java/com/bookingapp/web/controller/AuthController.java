package com.bookingapp.web.controller;

import com.bookingapp.web.dto.AuthResponse;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.domain.service.dto.AuthenticationResult;
import com.bookingapp.domain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration and login endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService
    ) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResult result = authService.register(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password()
        );
        return toResponse(result);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT bearer token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authService.login(request.email(), request.password());
        return toResponse(result);
    }

    private AuthResponse toResponse(AuthenticationResult result) {
        return new AuthResponse(
                result.accessToken(),
                "Bearer",
                result.userId(),
                result.email(),
                result.role()
        );
    }
}

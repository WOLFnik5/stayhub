package com.bookingapp.web.controller;

import com.bookingapp.infrastructure.security.AuthRateLimiter;
import com.bookingapp.service.AuthService;
import com.bookingapp.web.dto.AuthResponse;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.web.mapper.AuthWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Registration and login endpoints")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthWebMapper authWebMapper;
    private final AuthRateLimiter authRateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.checkRegister(httpRequest.getRemoteAddr());
        return authWebMapper.toResponse(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT bearer token")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.checkLogin(httpRequest.getRemoteAddr());
        return authWebMapper.toResponse(authService.login(request));
    }
}

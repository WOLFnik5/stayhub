package com.bookingapp.web.controller;

import com.bookingapp.service.AuthService;
import com.bookingapp.web.dto.AuthResponse;
import com.bookingapp.web.dto.AuthenticationResult;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.web.mapper.AuthWebMapper;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final AuthWebMapper authWebMapper;

    public AuthController(
            AuthService authService,
            AuthWebMapper authWebMapper
    ) {
        this.authService = authService;
        this.authWebMapper = authWebMapper;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        AuthenticationResult result = authService.register(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password()
        );
        return authWebMapper.toResponse(result);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        AuthenticationResult result = authService.login(request.email(), request.password());
        return authWebMapper.toResponse(result);
    }
}

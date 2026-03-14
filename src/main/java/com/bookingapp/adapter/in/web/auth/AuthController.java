package com.bookingapp.adapter.in.web.auth;

import com.bookingapp.application.model.AuthenticationResult;
import com.bookingapp.application.model.LoginCommand;
import com.bookingapp.application.model.RegisterUserCommand;
import com.bookingapp.application.port.in.auth.LoginUseCase;
import com.bookingapp.application.port.in.auth.RegisterUserUseCase;
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

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            LoginUseCase loginUseCase
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResult result = registerUserUseCase.register(new RegisterUserCommand(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password()
        ));
        return toResponse(result);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT bearer token")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = loginUseCase.login(new LoginCommand(
                request.email(),
                request.password()
        ));
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

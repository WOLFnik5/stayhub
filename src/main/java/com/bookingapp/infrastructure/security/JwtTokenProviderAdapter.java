package com.bookingapp.infrastructure.security;

import com.bookingapp.application.port.out.security.TokenProviderPort;
import com.bookingapp.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProviderAdapter implements TokenProviderPort {

    private final JwtTokenService jwtTokenService;

    public JwtTokenProviderAdapter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public String generateToken(User user) {
        return jwtTokenService.generateToken(user);
    }
}

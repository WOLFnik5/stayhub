package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.UserRole;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String email,
        UserRole role
) {
}

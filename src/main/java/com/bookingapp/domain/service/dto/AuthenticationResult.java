package com.bookingapp.domain.service.dto;

import com.bookingapp.domain.enums.UserRole;

public record AuthenticationResult(
        String accessToken,
        Long userId,
        String email,
        UserRole role
) {
}

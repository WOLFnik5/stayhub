package com.bookingapp.domain.service.dto;

import com.bookingapp.domain.enums.UserRole;

public record CurrentUser(
        Long id,
        String email,
        UserRole role
) {
}

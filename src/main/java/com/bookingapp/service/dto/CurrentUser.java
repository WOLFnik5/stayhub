package com.bookingapp.service.dto;

import com.bookingapp.domain.model.enums.UserRole;

public record CurrentUser(
        Long id,
        String email,
        UserRole role
) {
}

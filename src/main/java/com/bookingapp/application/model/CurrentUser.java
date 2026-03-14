package com.bookingapp.application.model;

import com.bookingapp.domain.enums.UserRole;

public record CurrentUser(
        Long id,
        String email,
        UserRole role
) {
}

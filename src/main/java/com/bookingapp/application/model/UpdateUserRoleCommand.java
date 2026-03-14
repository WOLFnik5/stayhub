package com.bookingapp.application.model;

import com.bookingapp.domain.enums.UserRole;

public record UpdateUserRoleCommand(
        Long userId,
        UserRole role
) {
}

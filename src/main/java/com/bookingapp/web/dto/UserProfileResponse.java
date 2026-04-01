package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.UserRole;

public record UserProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role
) {
}

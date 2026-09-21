package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import jakarta.validation.constraints.Email;

public record PatchCurrentUserRequest(
        @Email String email,
        String firstName,
        String lastName
) {
    public PatchCurrentUserRequest {
        email = normalize(email);
    }
}

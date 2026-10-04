package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record PatchCurrentUserRequest(
        @Email @Size(max = 255) String email,
        @Size(max = 255) String firstName,
        @Size(max = 255) String lastName
) {
    public PatchCurrentUserRequest {
        email = normalize(email);
    }
}

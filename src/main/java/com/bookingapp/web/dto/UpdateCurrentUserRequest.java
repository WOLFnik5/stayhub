package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName
) {
    public UpdateCurrentUserRequest {
        email = normalize(email);
    }
}

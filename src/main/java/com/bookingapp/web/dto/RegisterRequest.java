package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Size(min = 8, max = 255) String password
) {
    public RegisterRequest {
        email = normalize(email);
    }
}

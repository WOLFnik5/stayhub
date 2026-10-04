package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import com.bookingapp.web.validation.Utf8ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @NotBlank @Size(min = 8) @Utf8ByteLength(max = 72) String password
) {
    public RegisterRequest {
        email = normalize(email);
    }
}

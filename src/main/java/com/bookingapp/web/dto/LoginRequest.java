package com.bookingapp.web.dto;

import static com.bookingapp.service.validation.EmailNormalizationUtils.normalize;

import com.bookingapp.web.validation.Utf8ByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Utf8ByteLength(max = 72) String password
) {
    public LoginRequest {
        email = normalize(email);
    }
}

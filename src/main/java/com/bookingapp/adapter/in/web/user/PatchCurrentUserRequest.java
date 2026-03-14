package com.bookingapp.adapter.in.web.user;

import jakarta.validation.constraints.Email;

public record PatchCurrentUserRequest(
        @Email String email,
        String firstName,
        String lastName
) {
}

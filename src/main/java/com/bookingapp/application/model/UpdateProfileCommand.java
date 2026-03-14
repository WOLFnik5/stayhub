package com.bookingapp.application.model;

public record UpdateProfileCommand(
        String email,
        String firstName,
        String lastName
) {
}

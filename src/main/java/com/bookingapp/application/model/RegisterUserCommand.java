package com.bookingapp.application.model;

public record RegisterUserCommand(
        String email,
        String firstName,
        String lastName,
        String password
) {
}

package com.bookingapp.application.model;

public record LoginCommand(
        String email,
        String password
) {
}

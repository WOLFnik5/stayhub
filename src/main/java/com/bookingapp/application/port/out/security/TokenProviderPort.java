package com.bookingapp.application.port.out.security;

import com.bookingapp.domain.model.User;

public interface TokenProviderPort {

    String generateToken(User user);
}

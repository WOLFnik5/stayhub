package com.bookingapp.application.port.in.auth;

import com.bookingapp.application.model.AuthenticationResult;
import com.bookingapp.application.model.RegisterUserCommand;

public interface RegisterUserUseCase {

    AuthenticationResult register(RegisterUserCommand command);
}

package com.bookingapp.application.port.in.auth;

import com.bookingapp.application.model.AuthenticationResult;
import com.bookingapp.application.model.LoginCommand;

public interface LoginUseCase {

    AuthenticationResult login(LoginCommand command);
}

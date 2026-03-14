package com.bookingapp.application.port.out.security;

import com.bookingapp.application.model.CurrentUser;

public interface CurrentUserProviderPort {

    CurrentUser getCurrentUser();
}

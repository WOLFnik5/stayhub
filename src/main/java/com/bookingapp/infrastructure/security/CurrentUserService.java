package com.bookingapp.infrastructure.security;

import com.bookingapp.service.dto.CurrentUser;

public interface CurrentUserService {

    CurrentUser getCurrentUser();
}

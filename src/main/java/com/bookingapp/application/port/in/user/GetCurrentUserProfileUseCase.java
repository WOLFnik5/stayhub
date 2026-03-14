package com.bookingapp.application.port.in.user;

import com.bookingapp.domain.model.User;

public interface GetCurrentUserProfileUseCase {

    User getCurrentUserProfile();
}

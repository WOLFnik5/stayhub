package com.bookingapp.application.port.in.user;

import com.bookingapp.application.model.UpdateProfileCommand;
import com.bookingapp.domain.model.User;

public interface UpdateCurrentUserProfileUseCase {

    User updateCurrentUserProfile(UpdateProfileCommand command);
}

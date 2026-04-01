package com.bookingapp.web.mapper;

import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
import com.bookingapp.web.dto.UserProfileResponse;
import org.springframework.stereotype.Component;

@Component
public class UserWebMapper {

    public UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );
    }
}

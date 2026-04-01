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

    public String selectValue(String candidate, String currentValue, String fieldName) {
        if (candidate == null) {
            return currentValue;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException("Field '" + fieldName + "' must not be blank");
        }

        return trimmed;
    }
}

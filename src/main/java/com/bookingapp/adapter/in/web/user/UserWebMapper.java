package com.bookingapp.adapter.in.web.user;

import com.bookingapp.application.model.UpdateProfileCommand;
import com.bookingapp.application.model.UpdateUserRoleCommand;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
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

    public UpdateProfileCommand toUpdateProfileCommand(UpdateCurrentUserRequest request) {
        return new UpdateProfileCommand(
                request.email(),
                request.firstName(),
                request.lastName()
        );
    }

    public UpdateProfileCommand toPatchProfileCommand(PatchCurrentUserRequest request, User currentUser) {
        String email = selectValue(request.email(), currentUser.getEmail(), "email");
        String firstName = selectValue(request.firstName(), currentUser.getFirstName(), "firstName");
        String lastName = selectValue(request.lastName(), currentUser.getLastName(), "lastName");

        return new UpdateProfileCommand(email, firstName, lastName);
    }

    public UpdateUserRoleCommand toUpdateUserRoleCommand(Long userId, UpdateUserRoleRequest request) {
        return new UpdateUserRoleCommand(userId, request.role());
    }

    private String selectValue(String candidate, String currentValue, String fieldName) {
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

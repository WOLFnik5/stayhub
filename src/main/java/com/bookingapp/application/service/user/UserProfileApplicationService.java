package com.bookingapp.application.service.user;

import com.bookingapp.application.model.CurrentUser;
import com.bookingapp.application.model.UpdateProfileCommand;
import com.bookingapp.application.model.UpdateUserRoleCommand;
import com.bookingapp.application.port.in.user.GetCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateCurrentUserProfileUseCase;
import com.bookingapp.application.port.in.user.UpdateUserRoleUseCase;
import com.bookingapp.application.port.out.persistence.UserRepositoryPort;
import com.bookingapp.application.port.out.security.CurrentUserProviderPort;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileApplicationService implements
        GetCurrentUserProfileUseCase,
        UpdateCurrentUserProfileUseCase,
        UpdateUserRoleUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final CurrentUserProviderPort currentUserProviderPort;

    public UserProfileApplicationService(
            UserRepositoryPort userRepositoryPort,
            CurrentUserProviderPort currentUserProviderPort
    ) {
        this.userRepositoryPort = userRepositoryPort;
        this.currentUserProviderPort = currentUserProviderPort;
    }

    @Override
    public User getCurrentUserProfile() {
        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        return getUserById(currentUser.id());
    }

    @Override
    @Transactional
    public User updateCurrentUserProfile(UpdateProfileCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Update profile command must not be null");
        }

        CurrentUser currentUser = currentUserProviderPort.getCurrentUser();
        User existingUser = getUserById(currentUser.id());

        if (!existingUser.getEmail().equals(command.email()) && userRepositoryPort.existsByEmail(command.email())) {
            throw new BusinessValidationException("User with email '" + command.email() + "' already exists");
        }

        User updatedUser = existingUser.updateProfile(command.email(), command.firstName(), command.lastName());
        return userRepositoryPort.save(updatedUser);
    }

    @Override
    @Transactional
    public User updateUserRole(UpdateUserRoleCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Update user role command must not be null");
        }

        User existingUser = getUserById(command.userId());
        User updatedUser = existingUser.changeRole(command.role());
        return userRepositoryPort.save(updatedUser);
    }

    private User getUserById(Long userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '" + userId + "' was not found"));
    }
}

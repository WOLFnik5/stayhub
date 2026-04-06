package com.bookingapp.service;

import static com.bookingapp.service.validation.TextValidationUtils.requireNonBlank;
import static com.bookingapp.service.validation.TextValidationUtils.selectNonBlank;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.web.dto.PatchCurrentUserRequest;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepositoryImpl userRepository;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepositoryImpl userRepository,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    public User getCurrentUserProfile() {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        return getUserById(currentUser.id());
    }

    @Transactional
    public User updateCurrentUserProfile(UpdateCurrentUserRequest request) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        User existingUser = getUserById(currentUser.id());
        String normalizedEmail = requireNonBlank(request.email(), "User email must not be blank");

        if (!existingUser.getEmail().equals(normalizedEmail)
                && userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessValidationException(
                    "User with email '" + normalizedEmail + "' already exists"
            );
        }

        String normalizedFirstName = requireNonBlank(
                request.firstName(),
                "User first name must not be blank"
        );
        String normalizedLastName = requireNonBlank(
                request.lastName(),
                "User last name must not be blank"
        );
        existingUser.setEmail(normalizedEmail);
        existingUser.setFirstName(normalizedFirstName);
        existingUser.setLastName(normalizedLastName);
        return userRepository.save(existingUser);
    }

    @Transactional
    public User updateUserRole(Long userId, UserRole role) {
        User existingUser = getUserById(userId);
        if (role == null) {
            throw new BusinessValidationException("User role must not be null");
        }
        existingUser.setRole(role);
        return userRepository.save(existingUser);
    }

    @Transactional
    public User patchCurrentUserProfile(PatchCurrentUserRequest request) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        User existing = getUserById(currentUser.id());

        String email = selectNonBlank(request.email(), existing.getEmail(), "email");
        String firstName = selectNonBlank(
                request.firstName(),
                existing.getFirstName(),
                "firstName"
        );

        if (!existing.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("User with email '" + email + "' already exists");
        }

        String lastName = selectNonBlank(
                request.lastName(),
                existing.getLastName(),
                "lastName"
        );
        existing.setEmail(email);
        existing.setFirstName(firstName);
        existing.setLastName(lastName);
        return userRepository.save(existing);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '"
                        + userId
                        + "' was not found"));
    }
}

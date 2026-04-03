package com.bookingapp.service;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.web.dto.PatchCurrentUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public UserService(
            UserRepository userRepository,
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
    public User updateCurrentUserProfile(String email, String firstName, String lastName) {
        CurrentUser currentUser = currentUserService.getCurrentUser();
        User existingUser = getUserById(currentUser.id());
        String normalizedEmail = requireNonBlank(email, "User email must not be blank");

        if (!existingUser.getEmail().equals(normalizedEmail)
                && userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessValidationException(
                    "User with email '" + normalizedEmail + "' already exists"
            );
        }

        String normalizedFirstName = requireNonBlank(
                firstName,
                "User first name must not be blank"
        );
        String normalizedLastName = requireNonBlank(
                lastName,
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

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '"
                        + userId
                        + "' was not found"));
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

    private static String selectNonBlank(String candidate, String fallback, String fieldName) {
        if (candidate == null) {
            return fallback;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException("Field '" + fieldName + "' must not be blank");
        }
        return trimmed;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }
}

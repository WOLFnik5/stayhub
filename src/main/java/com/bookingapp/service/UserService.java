package com.bookingapp.service;

import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.service.dto.CurrentUser;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.web.dto.PatchCurrentUserRequest;
import jakarta.validation.Valid;
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

        if (!existingUser.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("User with email '" + email + "' already exists");
        }

        User updatedUser = existingUser.updateProfile(email, firstName, lastName);
        return userRepository.save(updatedUser);
    }

    @Transactional
    public User updateUserRole(Long userId, UserRole role) {
        User existingUser = getUserById(userId);
        User updatedUser = existingUser.changeRole(role);
        return userRepository.save(updatedUser);
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

        String email     = selectNonBlank(request.email(),     existing.getEmail(),     "email");
        String firstName = selectNonBlank(request.firstName(), existing.getFirstName(), "firstName");
        String lastName  = selectNonBlank(request.lastName(),  existing.getLastName(),  "lastName");

        if (!existing.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("User with email '" + email + "' already exists");
        }

        return userRepository.save(existing.updateProfile(email, firstName, lastName));
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
}

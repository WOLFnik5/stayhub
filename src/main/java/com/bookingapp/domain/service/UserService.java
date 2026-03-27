package com.bookingapp.domain.service;

import com.bookingapp.domain.service.dto.CurrentUser;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.exception.EntityNotFoundDomainException;
import com.bookingapp.domain.model.User;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.infrastructure.security.CurrentUserService;
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
    public User updateUserRole(Long userId, com.bookingapp.domain.enums.UserRole role) {
        User existingUser = getUserById(userId);
        User updatedUser = existingUser.changeRole(role);
        return userRepository.save(updatedUser);
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundDomainException("User with id '" + userId + "' was not found"));
    }
}

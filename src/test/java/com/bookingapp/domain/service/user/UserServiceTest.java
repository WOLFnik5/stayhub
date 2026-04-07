package com.bookingapp.domain.service.user;

import com.bookingapp.infrastructure.security.CurrentUser;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.service.UserService;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
import com.bookingapp.web.dto.UpdateCurrentUserRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepositoryImpl userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private UserService userService;

    @Test
    void getCurrentUserProfileShouldReturnPersistedUser() {
        CurrentUser currentUser = new CurrentUser(15L, "old@example.com", UserRole.CUSTOMER);
        User existingUser = new User(15L, "old@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER);
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findById(15L)).thenReturn(Optional.of(existingUser));

        User result = userService.getCurrentUserProfile();

        assertThat(result).isEqualTo(existingUser);
    }

    @Test
    void updateCurrentUserProfileShouldPersistUpdatedValues() {
        CurrentUser currentUser = new CurrentUser(15L, "old@example.com", UserRole.CUSTOMER);
        User existingUser = new User(15L, "old@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER);
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findById(15L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest(
                "new@example.com",
                "Jane",
                "Smith"
        );

        User result = userService.updateCurrentUserProfile(request);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    void updateCurrentUserProfileShouldRejectDuplicateEmail() {
        CurrentUser currentUser = new CurrentUser(15L, "old@example.com", UserRole.CUSTOMER);
        User existingUser = new User(15L, "old@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER);
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findById(15L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        UpdateCurrentUserRequest request = new UpdateCurrentUserRequest(
                "taken@example.com",
                "Jane",
                "Smith"
        );

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(request))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateUserRoleShouldPersistChangedRole() {
        User existingUser = new User(15L, "user@example.com", "John", "Doe", "encoded", UserRole.CUSTOMER);
        when(userRepository.findById(15L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateUserRole(15L, UserRole.ADMIN);

        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
    }
}
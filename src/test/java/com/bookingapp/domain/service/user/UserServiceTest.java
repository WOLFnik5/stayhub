package com.bookingapp.domain.service.user;

import com.bookingapp.service.dto.CurrentUser;
import com.bookingapp.service.UserService;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.infrastructure.security.CurrentUserService;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
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
    private UserRepository userRepository;

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

        User result = userService.updateCurrentUserProfile("new@example.com", "Jane", "Smith");

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

        assertThatThrownBy(() -> userService.updateCurrentUserProfile("taken@example.com", "Jane", "Smith"))
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

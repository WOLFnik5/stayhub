package com.bookingapp.domain.service.auth;

import com.bookingapp.web.dto.AuthenticationResult;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.service.AuthService;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryImpl userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldCreateCustomerWithEncodedPassword() {
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new User(
                    1L,
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPassword(),
                    user.getRole()
            );
        });
        when(jwtTokenService.generateToken(any(User.class))).thenReturn("jwt-token");

        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "John",
                "Doe",
                "raw-password"
        );

        AuthenticationResult result = authService.register(request);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        verify(passwordEncoder).encode("raw-password");
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "customer@example.com",
                "John",
                "Doe",
                "raw-password"
        );

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() {
        User existingUser = new User(
                7L,
                "admin@example.com",
                "Admin",
                "User",
                "encoded-password",
                UserRole.ADMIN
        );
        when(userRepository.findByEmail("admin@example.com")).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches("raw-password", "encoded-password")).thenReturn(true);
        when(jwtTokenService.generateToken(existingUser)).thenReturn("login-token");

        LoginRequest request = new LoginRequest("admin@example.com", "raw-password");

        AuthenticationResult result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("login-token");
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.email()).isEqualTo("admin@example.com");
        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void loginShouldRejectUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(java.util.Optional.empty());

        LoginRequest request = new LoginRequest("missing@example.com", "raw-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void loginShouldRejectInvalidPassword() {
        User existingUser = new User(
                7L,
                "admin@example.com",
                "Admin",
                "User",
                "encoded-password",
                UserRole.ADMIN
        );
        when(userRepository.findByEmail("admin@example.com")).thenReturn(java.util.Optional.of(existingUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        LoginRequest request = new LoginRequest("admin@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
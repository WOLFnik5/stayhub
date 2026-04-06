package com.bookingapp.service;

import static com.bookingapp.service.validation.TextValidationUtils.requireNonBlank;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.persistence.UserRepositoryImpl;
import com.bookingapp.web.dto.AuthenticationResult;
import com.bookingapp.web.dto.LoginRequest;
import com.bookingapp.web.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepositoryImpl userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepositoryImpl userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthenticationResult register(RegisterRequest request) {
        String normalizedEmail = requireNonBlank(
                request.email(), "User email must not be blank"
        );
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessValidationException(
                    "User with email '" + normalizedEmail + "' already exists"
            );
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User userToSave = new User(
                null,
                normalizedEmail,
                requireNonBlank(request.firstName(), "User first name must not be blank"),
                requireNonBlank(request.lastName(), "User last name must not be blank"),
                requireNonBlank(encodedPassword, "User password must not be blank"),
                UserRole.CUSTOMER
        );

        User savedUser = userRepository.save(userToSave);
        String accessToken = jwtTokenService.generateToken(savedUser);
        return new AuthenticationResult(
                accessToken, savedUser.getId(), savedUser.getEmail(), savedUser.getRole()
        );
    }

    public AuthenticationResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessValidationException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessValidationException("Invalid email or password");
        }

        String accessToken = jwtTokenService.generateToken(user);
        return new AuthenticationResult(
                accessToken, user.getId(), user.getEmail(), user.getRole()
        );
    }
}

package com.bookingapp.service;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.model.enums.UserRole;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.exception.BusinessValidationException;
import com.bookingapp.infrastructure.security.JwtTokenService;
import com.bookingapp.web.dto.AuthenticationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public AuthenticationResult register(
            String email,
            String firstName,
            String lastName,
            String password
    ) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("User with email '" + email + "' already exists");
        }

        String encodedPassword = passwordEncoder.encode(password);
        User userToSave = User.createNew(
                email,
                firstName,
                lastName,
                encodedPassword,
                UserRole.CUSTOMER
        );

        User savedUser = userRepository.save(userToSave);
        String accessToken = jwtTokenService.generateToken(savedUser);
        return new AuthenticationResult(
                accessToken, savedUser.getId(), savedUser.getEmail(), savedUser.getRole()
        );
    }

    public AuthenticationResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessValidationException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessValidationException("Invalid email or password");
        }

        String accessToken = jwtTokenService.generateToken(user);
        return new AuthenticationResult(accessToken, user.getId(), user.getEmail(), user.getRole());
    }
}

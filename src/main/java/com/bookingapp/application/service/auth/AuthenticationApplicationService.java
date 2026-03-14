package com.bookingapp.application.service.auth;

import com.bookingapp.application.model.AuthenticationResult;
import com.bookingapp.application.model.LoginCommand;
import com.bookingapp.application.model.RegisterUserCommand;
import com.bookingapp.application.port.in.auth.LoginUseCase;
import com.bookingapp.application.port.in.auth.RegisterUserUseCase;
import com.bookingapp.application.port.out.persistence.UserRepositoryPort;
import com.bookingapp.application.port.out.security.PasswordEncoderPort;
import com.bookingapp.application.port.out.security.TokenProviderPort;
import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticationApplicationService implements RegisterUserUseCase, LoginUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final TokenProviderPort tokenProviderPort;

    public AuthenticationApplicationService(
            UserRepositoryPort userRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            TokenProviderPort tokenProviderPort
    ) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.tokenProviderPort = tokenProviderPort;
    }

    @Override
    @Transactional
    public AuthenticationResult register(RegisterUserCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Register command must not be null");
        }

        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new BusinessValidationException("User with email '" + command.email() + "' already exists");
        }

        String encodedPassword = passwordEncoderPort.encode(command.password());
        User userToSave = User.createNew(
                command.email(),
                command.firstName(),
                command.lastName(),
                encodedPassword,
                UserRole.CUSTOMER
        );

        User savedUser = userRepositoryPort.save(userToSave);
        String accessToken = tokenProviderPort.generateToken(savedUser);
        return new AuthenticationResult(accessToken, savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
    }

    @Override
    public AuthenticationResult login(LoginCommand command) {
        if (command == null) {
            throw new BusinessValidationException("Login command must not be null");
        }

        User user = userRepositoryPort.findByEmail(command.email())
                .orElseThrow(() -> new BusinessValidationException("Invalid email or password"));

        if (!passwordEncoderPort.matches(command.password(), user.getPassword())) {
            throw new BusinessValidationException("Invalid email or password");
        }

        String accessToken = tokenProviderPort.generateToken(user);
        return new AuthenticationResult(accessToken, user.getId(), user.getEmail(), user.getRole());
    }
}

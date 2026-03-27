package com.bookingapp.infrastructure.security;

import com.bookingapp.domain.service.dto.CurrentUser;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCurrentUserService implements CurrentUserService {

    @Override
    public CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Current user is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal)) {
            throw new AuthenticationCredentialsNotFoundException("Current user principal is not available");
        }

        return new CurrentUser(
                authenticatedUserPrincipal.userId(),
                authenticatedUserPrincipal.email(),
                authenticatedUserPrincipal.role()
        );
    }
}

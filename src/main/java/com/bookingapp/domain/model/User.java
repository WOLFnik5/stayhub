package com.bookingapp.domain.model;

import com.bookingapp.domain.enums.UserRole;
import com.bookingapp.domain.exception.BusinessValidationException;
import java.util.Objects;

public final class User {

    private final Long id;
    private final String email;
    private final String firstName;
    private final String lastName;
    /**
     * Password must be encoded before persistence.
     */
    private final String password;
    private final UserRole role;

    public User(
            Long id,
            String email,
            String firstName,
            String lastName,
            String password,
            UserRole role
    ) {
        this.id = id;
        this.email = requireNonBlank(email, "User email must not be blank");
        this.firstName = requireNonBlank(firstName, "User first name must not be blank");
        this.lastName = requireNonBlank(lastName, "User last name must not be blank");
        this.password = requireNonBlank(password, "User password must not be blank");
        this.role = Objects.requireNonNull(role, "User role must not be null");
    }

    public static User createNew(
            String email,
            String firstName,
            String lastName,
            String password,
            UserRole role
    ) {
        return new User(null, email, firstName, lastName, password, role);
    }

    public User updateProfile(String email, String firstName, String lastName) {
        return new User(id, email, firstName, lastName, password, role);
    }

    public User updatePassword(String password) {
        return new User(id, email, firstName, lastName, password, role);
    }

    public User changeRole(UserRole role) {
        return new User(id, email, firstName, lastName, password, role);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }
}

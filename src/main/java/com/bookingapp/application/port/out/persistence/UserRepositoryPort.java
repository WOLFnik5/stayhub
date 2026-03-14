package com.bookingapp.application.port.out.persistence;

import com.bookingapp.domain.model.User;

import java.util.Optional;

public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

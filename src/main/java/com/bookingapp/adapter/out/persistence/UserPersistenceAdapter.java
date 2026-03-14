package com.bookingapp.adapter.out.persistence;

import com.bookingapp.adapter.out.persistence.mapper.UserPersistenceMapper;
import com.bookingapp.adapter.out.persistence.repository.JpaUserRepository;
import com.bookingapp.application.port.out.persistence.UserRepositoryPort;
import com.bookingapp.domain.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final JpaUserRepository jpaUserRepository;
    private final UserPersistenceMapper userPersistenceMapper;

    public UserPersistenceAdapter(
            JpaUserRepository jpaUserRepository,
            UserPersistenceMapper userPersistenceMapper
    ) {
        this.jpaUserRepository = jpaUserRepository;
        this.userPersistenceMapper = userPersistenceMapper;
    }

    @Override
    @Transactional
    public User save(User user) {
        return userPersistenceMapper.toDomain(
                jpaUserRepository.save(userPersistenceMapper.toEntity(user))
        );
    }

    @Override
    public Optional<User> findById(Long userId) {
        return jpaUserRepository.findById(userId)
                .map(userPersistenceMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaUserRepository.findByEmail(email)
                .map(userPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }
}

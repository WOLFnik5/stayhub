package com.bookingapp.infrastructure.persistence;

import com.bookingapp.domain.model.User;
import com.bookingapp.domain.repository.UserRepository;
import com.bookingapp.infrastructure.persistence.mapper.UserPersistenceMapper;
import com.bookingapp.infrastructure.persistence.repository.JpaUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class UserRepositoryImpl implements UserRepository {

    private final JpaUserRepository jpaUserRepository;
    private final UserPersistenceMapper userPersistenceMapper;

    public UserRepositoryImpl(
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

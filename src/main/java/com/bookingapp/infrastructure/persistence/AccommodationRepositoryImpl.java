package com.bookingapp.infrastructure.persistence;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.infrastructure.persistence.mapper.AccommodationPersistenceMapper;
import com.bookingapp.infrastructure.persistence.repository.JpaAccommodationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AccommodationRepositoryImpl implements AccommodationRepository {

    private final JpaAccommodationRepository jpaAccommodationRepository;
    private final AccommodationPersistenceMapper accommodationPersistenceMapper;

    public AccommodationRepositoryImpl(
            JpaAccommodationRepository jpaAccommodationRepository,
            AccommodationPersistenceMapper accommodationPersistenceMapper
    ) {
        this.jpaAccommodationRepository = jpaAccommodationRepository;
        this.accommodationPersistenceMapper = accommodationPersistenceMapper;
    }

    @Override
    @Transactional
    public Accommodation save(Accommodation accommodation) {
        return accommodationPersistenceMapper.toDomain(
                jpaAccommodationRepository
                        .save(accommodationPersistenceMapper.toEntity(accommodation))
        );
    }

    @Override
    public Optional<Accommodation> findById(Long accommodationId) {
        return jpaAccommodationRepository.findById(accommodationId)
                .map(accommodationPersistenceMapper::toDomain);
    }

    @Override
    public List<Accommodation> findAll() {
        return jpaAccommodationRepository.findAll().stream()
                .map(accommodationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long accommodationId) {
        return jpaAccommodationRepository.existsById(accommodationId);
    }

    @Override
    @Transactional
    public void deleteById(Long accommodationId) {
        jpaAccommodationRepository.deleteById(accommodationId);
    }
}

package com.bookingapp.adapter.out.persistence;

import com.bookingapp.adapter.out.persistence.mapper.AccommodationPersistenceMapper;
import com.bookingapp.adapter.out.persistence.repository.JpaAccommodationRepository;
import com.bookingapp.application.port.out.persistence.AccommodationRepositoryPort;
import com.bookingapp.domain.model.Accommodation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class AccommodationPersistenceAdapter implements AccommodationRepositoryPort {

    private final JpaAccommodationRepository jpaAccommodationRepository;
    private final AccommodationPersistenceMapper accommodationPersistenceMapper;

    public AccommodationPersistenceAdapter(
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
                jpaAccommodationRepository.save(accommodationPersistenceMapper.toEntity(accommodation))
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

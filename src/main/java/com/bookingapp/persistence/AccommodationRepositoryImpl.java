package com.bookingapp.persistence;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.repository.AccommodationRepository;
import com.bookingapp.persistence.entity.AccommodationEntity;
import com.bookingapp.persistence.mapper.AccommodationPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AccommodationRepositoryImpl implements AccommodationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final AccommodationPersistenceMapper accommodationPersistenceMapper;

    public AccommodationRepositoryImpl(
            AccommodationPersistenceMapper accommodationPersistenceMapper
    ) {
        this.accommodationPersistenceMapper = accommodationPersistenceMapper;
    }

    @Override
    @Transactional
    public Accommodation save(Accommodation accommodation) {
        AccommodationEntity entity = accommodationPersistenceMapper.toEntity(accommodation);
        if (accommodation.getId() == null) {
            entityManager.persist(entity);
            entityManager.flush();
            return accommodationPersistenceMapper.toDomain(entity);
        } else {
            AccommodationEntity merged = entityManager.merge(entity);
            return accommodationPersistenceMapper.toDomain(merged);
        }
    }

    @Override
    public Optional<Accommodation> findById(Long accommodationId) {
        AccommodationEntity entity = entityManager.find(AccommodationEntity.class, accommodationId);
        return Optional.ofNullable(entity)
                .map(accommodationPersistenceMapper::toDomain);
    }

    @Override
    public List<Accommodation> findAll() {
        TypedQuery<AccommodationEntity> query = entityManager.createQuery(
                "SELECT a FROM AccommodationEntity a",
                AccommodationEntity.class
        );
        return query.getResultList().stream()
                .map(accommodationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long accommodationId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(a) FROM AccommodationEntity a WHERE a.id = :id",
                Long.class
        );
        query.setParameter("id", accommodationId);
        return query.getSingleResult() > 0;
    }

    @Override
    @Transactional
    public void deleteById(Long accommodationId) {
        AccommodationEntity entity = entityManager.find(AccommodationEntity.class, accommodationId);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }
}

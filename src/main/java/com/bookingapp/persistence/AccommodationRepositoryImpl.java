package com.bookingapp.persistence;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.PageResult;
import com.bookingapp.persistence.entity.AccommodationEntity;
import com.bookingapp.persistence.mapper.AccommodationPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AccommodationRepositoryImpl {

    @PersistenceContext
    private EntityManager entityManager;

    private final AccommodationPersistenceMapper accommodationPersistenceMapper;

    public AccommodationRepositoryImpl(
            AccommodationPersistenceMapper accommodationPersistenceMapper
    ) {
        this.accommodationPersistenceMapper = accommodationPersistenceMapper;
    }

    @Transactional
    public Accommodation save(Accommodation accommodation) {
        AccommodationEntity entity = accommodationPersistenceMapper.toEntity(accommodation);

        if (accommodation.getId() == null) {
            entityManager.persist(entity);
            entityManager.flush();
            return accommodationPersistenceMapper.toDomain(entity);
        }

        AccommodationEntity merged = entityManager.merge(entity);
        return accommodationPersistenceMapper.toDomain(merged);
    }

    public Optional<Accommodation> findById(Long accommodationId) {
        AccommodationEntity entity = entityManager.find(
                AccommodationEntity.class,
                accommodationId
        );

        return Optional.ofNullable(entity)
                .map(accommodationPersistenceMapper::toDomain);
    }

    @Transactional
    public Optional<Accommodation> findByIdForUpdate(Long accommodationId) {
        AccommodationEntity entity = entityManager.find(
                AccommodationEntity.class,
                accommodationId,
                LockModeType.PESSIMISTIC_WRITE
        );

        return Optional.ofNullable(entity)
                .map(accommodationPersistenceMapper::toDomain);
    }

    public PageResult<Accommodation> findAvailablePage(int page, int size) {
        long totalElements = countAvailable();

        if (totalElements == 0) {
            return new PageResult<>(
                    List.of(),
                    page,
                    size,
                    0
            );
        }

        List<Long> ids = findAvailableIds(page, size);

        if (ids.isEmpty()) {
            return new PageResult<>(
                    List.of(),
                    page,
                    size,
                    totalElements
            );
        }

        List<AccommodationEntity> entities = findAllWithAmenitiesByIds(ids);

        Map<Long, AccommodationEntity> entitiesById = entities.stream()
                .collect(Collectors.toMap(
                        AccommodationEntity::getId,
                        Function.identity()
                ));

        List<Accommodation> content = ids.stream()
                .map(entitiesById::get)
                .filter(java.util.Objects::nonNull)
                .map(accommodationPersistenceMapper::toDomain)
                .toList();

        return new PageResult<>(
                content,
                page,
                size,
                totalElements
        );
    }

    public boolean existsById(Long accommodationId) {
        TypedQuery<Long> query = entityManager.createQuery(
                """
                SELECT COUNT(a)
                FROM AccommodationEntity a
                WHERE a.id = :id
                """,
                Long.class
        );

        query.setParameter("id", accommodationId);

        return query.getSingleResult() > 0;
    }

    @Transactional
    public void deleteById(Long accommodationId) {
        AccommodationEntity entity = entityManager.find(
                AccommodationEntity.class,
                accommodationId
        );

        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    private long countAvailable() {
        return entityManager.createQuery(
                        """
                        SELECT COUNT(a)
                        FROM AccommodationEntity a
                        WHERE a.availability > 0
                        """,
                        Long.class
                )
                .getSingleResult();
    }

    private List<Long> findAvailableIds(int page, int size) {
        return entityManager.createQuery(
                        """
                        SELECT a.id
                        FROM AccommodationEntity a
                        WHERE a.availability > 0
                        ORDER BY a.id ASC
                        """,
                        Long.class
                )
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    private List<AccommodationEntity> findAllWithAmenitiesByIds(List<Long> ids) {
        return entityManager.createQuery(
                        """
                        SELECT DISTINCT a
                        FROM AccommodationEntity a
                        LEFT JOIN FETCH a.amenities
                        WHERE a.id IN :ids
                        """,
                        AccommodationEntity.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }
}
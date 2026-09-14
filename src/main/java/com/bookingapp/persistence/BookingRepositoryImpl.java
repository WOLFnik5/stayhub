package com.bookingapp.persistence;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.persistence.entity.BookingEntity;
import com.bookingapp.persistence.mapper.BookingPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class BookingRepositoryImpl {

    private static final List<BookingStatus> INACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.CANCELED,
            BookingStatus.EXPIRED
    );

    @PersistenceContext
    private EntityManager entityManager;

    private final BookingPersistenceMapper bookingPersistenceMapper;

    public BookingRepositoryImpl(
            BookingPersistenceMapper bookingPersistenceMapper
    ) {
        this.bookingPersistenceMapper = bookingPersistenceMapper;
    }

    @Transactional
    public Booking save(Booking booking) {
        BookingEntity entity = bookingPersistenceMapper.toEntity(booking);
        if (booking.getId() == null) {
            entityManager.persist(entity);
            entityManager.flush();
            return bookingPersistenceMapper.toDomain(entity);
        } else {
            BookingEntity merged = entityManager.merge(entity);
            return bookingPersistenceMapper.toDomain(merged);
        }
    }

    public Optional<Booking> findById(Long bookingId) {
        BookingEntity entity = entityManager.find(BookingEntity.class, bookingId);
        return Optional.ofNullable(entity)
                .map(bookingPersistenceMapper::toDomain);
    }

    @Transactional
    public Optional<Booking> findByIdForUpdate(Long bookingId) {
        BookingEntity entity = entityManager.find(
                BookingEntity.class, bookingId, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) {
            return Optional.empty();
        }
        // Reload snapshots already present in the persistence context after taking the lock.
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(bookingPersistenceMapper.toDomain(entity));
    }

    public List<Booking> findAllByFilter(BookingFilterQuery query) {
        TypedQuery<BookingEntity> jpqlQuery = entityManager.createQuery(
                """
                SELECT b
                FROM BookingEntity b
                WHERE (:userId IS NULL OR b.userId = :userId)
                  AND (:status IS NULL OR b.status = :status)
                ORDER BY b.checkInDate ASC, b.id DESC
                """,
                BookingEntity.class
        );
        jpqlQuery.setParameter("userId", query.userId());
        jpqlQuery.setParameter("status", query.status());
        return jpqlQuery.getResultList().stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }

    public List<Booking> findAllByUserId(Long userId) {
        TypedQuery<BookingEntity> query = entityManager.createQuery(
                """
                SELECT b
                FROM BookingEntity b
                WHERE b.userId = :userId
                ORDER BY b.checkInDate ASC, b.id DESC
                """,
                BookingEntity.class
        );
        query.setParameter("userId", userId);
        return query.getResultList().stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }

    public long countActiveBookingOverlaps(
            Long accommodationId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long excludedBookingId
    ) {
        TypedQuery<Long> query = entityManager.createQuery(
                """
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.accommodationId = :accommodationId
                  AND b.status NOT IN :inactiveStatuses
                  AND (:excludedBookingId IS NULL OR b.id <> :excludedBookingId)
                  AND b.checkInDate < :checkOutDate
                  AND :checkInDate < b.checkOutDate
                """,
                Long.class
        );
        query.setParameter("accommodationId", accommodationId);
        query.setParameter("checkInDate", checkInDate);
        query.setParameter("checkOutDate", checkOutDate);
        query.setParameter("excludedBookingId", excludedBookingId);
        query.setParameter("inactiveStatuses", INACTIVE_BOOKING_STATUSES);
        return query.getSingleResult();
    }

    public List<Booking> findBookingsToExpire(LocalDate businessDate) {
        TypedQuery<BookingEntity> query = entityManager.createQuery(
                """
                SELECT b
                FROM BookingEntity b
                WHERE b.checkOutDate <= :businessDate
                  AND b.status NOT IN :inactiveStatuses
                ORDER BY b.checkOutDate ASC, b.id ASC
                """,
                BookingEntity.class
        );
        query.setParameter("businessDate", businessDate);
        query.setParameter("inactiveStatuses", INACTIVE_BOOKING_STATUSES);
        return query.getResultList().stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }
}

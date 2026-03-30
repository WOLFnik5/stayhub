package com.bookingapp.infrastructure.persistence;

import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.repository.BookingRepository;
import com.bookingapp.domain.service.dto.BookingFilterQuery;
import com.bookingapp.infrastructure.persistence.mapper.BookingPersistenceMapper;
import com.bookingapp.infrastructure.persistence.repository.JpaBookingRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class BookingRepositoryImpl implements BookingRepository {

    private static final List<BookingStatus> INACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.CANCELED,
            BookingStatus.EXPIRED
    );

    private final JpaBookingRepository jpaBookingRepository;
    private final BookingPersistenceMapper bookingPersistenceMapper;

    public BookingRepositoryImpl(
            JpaBookingRepository jpaBookingRepository,
            BookingPersistenceMapper bookingPersistenceMapper
    ) {
        this.jpaBookingRepository = jpaBookingRepository;
        this.bookingPersistenceMapper = bookingPersistenceMapper;
    }

    @Override
    @Transactional
    public Booking save(Booking booking) {
        return bookingPersistenceMapper.toDomain(
                jpaBookingRepository.save(bookingPersistenceMapper.toEntity(booking))
        );
    }

    @Override
    public Optional<Booking> findById(Long bookingId) {
        return jpaBookingRepository.findById(bookingId)
                .map(bookingPersistenceMapper::toDomain);
    }

    @Override
    public List<Booking> findAllByFilter(BookingFilterQuery query) {
        return jpaBookingRepository.findAllByFilter(query.userId(), query.status()).stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Booking> findAllByUserId(Long userId) {
        return jpaBookingRepository.findAllByUserIdOrderByCheckInDateAscIdDesc(userId).stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsActiveBookingOverlap(
            Long accommodationId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            Long excludedBookingId
    ) {
        return jpaBookingRepository.countActiveOverlappingBookings(
                accommodationId,
                checkInDate,
                checkOutDate,
                excludedBookingId,
                INACTIVE_BOOKING_STATUSES
        ) > 0;
    }

    @Override
    public List<Booking> findBookingsToExpire(LocalDate businessDate) {
        return jpaBookingRepository
                .findBookingsToExpire(businessDate, INACTIVE_BOOKING_STATUSES).stream()
                .map(bookingPersistenceMapper::toDomain)
                .toList();
    }
}

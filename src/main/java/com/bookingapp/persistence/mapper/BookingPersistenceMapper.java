package com.bookingapp.persistence.mapper;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.persistence.entity.BookingEntity;
import org.springframework.stereotype.Component;

@Component
public class BookingPersistenceMapper {

    public BookingEntity toEntity(Booking domain) {
        BookingEntity entity = new BookingEntity();
        entity.setId(domain.getId());
        entity.setCheckInDate(domain.getCheckInDate());
        entity.setCheckOutDate(domain.getCheckOutDate());
        entity.setAccommodationId(domain.getAccommodationId());
        entity.setUserId(domain.getUserId());
        entity.setStatus(domain.getStatus());
        return entity;
    }

    public Booking toDomain(BookingEntity entity) {
        return new Booking(
                entity.getId(),
                entity.getCheckInDate(),
                entity.getCheckOutDate(),
                entity.getAccommodationId(),
                entity.getUserId(),
                entity.getStatus()
        );
    }
}

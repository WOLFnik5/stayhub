package com.bookingapp.persistence.mapper;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.entity.BookingEntity;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface BookingPersistenceMapper {

    BookingEntity toEntity(Booking domain);

    Booking toDomain(BookingEntity entity);
}

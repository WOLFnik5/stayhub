package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.repository.BookingFilterQuery;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.web.dto.AccommodationSummaryResponse;
import com.bookingapp.web.dto.BookingDetailResponse;
import com.bookingapp.web.dto.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface BookingWebMapper {

    default BookingFilterQuery toFilterQuery(Long userId, BookingStatus status) {
        return new BookingFilterQuery(userId, status);
    }

    BookingResponse toResponse(Booking booking);

    AccommodationSummaryResponse toAccommodationSummaryResponse(Accommodation accommodation);

    @Mapping(target = "id", source = "booking.id")
    @Mapping(target = "checkInDate", source = "booking.checkInDate")
    @Mapping(target = "checkOutDate", source = "booking.checkOutDate")
    @Mapping(target = "accommodationId", source = "booking.accommodationId")
    @Mapping(target = "userId", source = "booking.userId")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "accommodation", source = "accommodation")
    BookingDetailResponse toDetailResponse(Booking booking, Accommodation accommodation);
}

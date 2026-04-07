package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.infrastructure.config.MapStructConfig;
import com.bookingapp.persistence.BookingFilterQuery;
import com.bookingapp.web.dto.BookingDetail;
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

    @Mapping(source = "detail.booking.id", target = "id")
    @Mapping(source = "detail.booking.checkInDate", target = "checkInDate")
    @Mapping(source = "detail.booking.checkOutDate", target = "checkOutDate")
    @Mapping(source = "detail.booking.status", target = "status")
    @Mapping(source = "detail.booking.userId", target = "userId")
    @Mapping(source = "detail.accommodation", target = "accommodation")
    @Mapping(source = "detail.booking.accommodationId", target = "accommodationId")
    BookingDetailResponse toDetailResponse(BookingDetail detail);
}

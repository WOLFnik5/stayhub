package com.bookingapp.web.mapper;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.service.dto.BookingFilterQuery;
import com.bookingapp.web.dto.AccommodationSummaryResponse;
import com.bookingapp.web.dto.BookingDetailResponse;
import com.bookingapp.web.dto.BookingResponse;
import org.springframework.stereotype.Component;

@Component
public class BookingWebMapper {

    public BookingFilterQuery toFilterQuery(Long userId, BookingStatus status) {
        return new BookingFilterQuery(userId, status);
    }

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getAccommodationId(),
                booking.getUserId(),
                booking.getStatus()
        );
    }

    public BookingDetailResponse toDetailResponse(Booking booking, Accommodation accommodation) {
        return new BookingDetailResponse(
                booking.getId(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getAccommodationId(),
                booking.getUserId(),
                booking.getStatus(),
                new AccommodationSummaryResponse(
                        accommodation.getId(),
                        accommodation.getType(),
                        accommodation.getLocation(),
                        accommodation.getSize()
                )
        );
    }
}

package com.bookingapp.adapter.in.web.mapper;

import com.bookingapp.adapter.in.web.dto.AccommodationSummaryResponse;
import com.bookingapp.adapter.in.web.dto.BookingDetailResponse;
import com.bookingapp.adapter.in.web.dto.BookingResponse;
import com.bookingapp.adapter.in.web.dto.CreateBookingRequest;
import com.bookingapp.adapter.in.web.dto.PatchBookingRequest;
import com.bookingapp.adapter.in.web.dto.UpdateBookingRequest;
import com.bookingapp.application.dto.BookingFilterQuery;
import com.bookingapp.application.dto.CreateBookingCommand;
import com.bookingapp.application.dto.UpdateBookingCommand;
import com.bookingapp.domain.enums.BookingStatus;
import com.bookingapp.domain.exception.BusinessValidationException;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class BookingWebMapper {

    public CreateBookingCommand toCreateCommand(CreateBookingRequest request) {
        return new CreateBookingCommand(
                request.accommodationId(),
                request.checkInDate(),
                request.checkOutDate()
        );
    }

    public UpdateBookingCommand toUpdateCommand(Long bookingId, UpdateBookingRequest request) {
        return new UpdateBookingCommand(
                bookingId,
                request.checkInDate(),
                request.checkOutDate()
        );
    }

    public UpdateBookingCommand toPatchCommand(Long bookingId, PatchBookingRequest request, Booking currentBooking) {
        LocalDate checkInDate = request.checkInDate() != null ? request.checkInDate() : currentBooking.getCheckInDate();
        LocalDate checkOutDate = request.checkOutDate() != null ? request.checkOutDate() : currentBooking.getCheckOutDate();

        if (checkInDate == null || checkOutDate == null) {
            throw new BusinessValidationException("Booking dates must not be null");
        }

        return new UpdateBookingCommand(bookingId, checkInDate, checkOutDate);
    }

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

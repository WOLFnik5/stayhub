package com.bookingapp.web.controller;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.service.BookingService;
import com.bookingapp.web.dto.BookingDetailResponse;
import com.bookingapp.web.dto.BookingResponse;
import com.bookingapp.web.dto.CreateBookingRequest;
import com.bookingapp.web.dto.PatchBookingRequest;
import com.bookingapp.web.dto.UpdateBookingRequest;
import com.bookingapp.web.mapper.BookingWebMapper;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController implements BookingApi {

    private final BookingService bookingService;
    private final BookingWebMapper bookingWebMapper;

    public BookingController(
            BookingService bookingService,
            BookingWebMapper bookingWebMapper
    ) {
        this.bookingService = bookingService;
        this.bookingWebMapper = bookingWebMapper;
    }

    @Override
    public BookingResponse createBooking(CreateBookingRequest request) {
        Booking createdBooking = bookingService.createBooking(
                request.accommodationId(),
                request.checkInDate(),
                request.checkOutDate()
        );
        return bookingWebMapper.toResponse(createdBooking);
    }

    @Override
    public List<BookingResponse> listBookings(Long userId, BookingStatus status) {
        return bookingService.listBookings(bookingWebMapper.toFilterQuery(userId, status)).stream()
                .map(bookingWebMapper::toResponse)
                .toList();
    }

    @Override
    public List<BookingResponse> listMyBookings() {
        return bookingService.listMyBookings().stream()
                .map(bookingWebMapper::toResponse)
                .toList();
    }

    @Override
    public BookingDetailResponse getBookingById(Long id) {
        Booking booking = bookingService.getBookingById(id);
        Accommodation accommodation = bookingService.getAccommodationByBookingId(id);
        return bookingWebMapper.toDetailResponse(booking, accommodation);
    }

    @Override
    public BookingResponse updateBooking(Long id, UpdateBookingRequest request) {
        Booking updatedBooking = bookingService.updateBooking(id,
                request.checkInDate(), request.checkOutDate());
        return bookingWebMapper.toResponse(updatedBooking);
    }

    @Override
    public BookingResponse patchBooking(Long id, PatchBookingRequest request) {
        Booking updatedBooking = bookingService.patchBooking(id, request);
        return bookingWebMapper.toResponse(updatedBooking);
    }

    @Override
    public BookingResponse cancelBooking(Long id) {
        Booking canceledBooking = bookingService.cancelBooking(id);
        return bookingWebMapper.toResponse(canceledBooking);
    }
}

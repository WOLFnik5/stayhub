package com.bookingapp.web.controller;

import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;
import com.bookingapp.service.BookingService;
import com.bookingapp.web.dto.BookingDetailResponse;
import com.bookingapp.web.dto.BookingResponse;
import com.bookingapp.web.dto.CreateBookingRequest;
import com.bookingapp.web.dto.PatchBookingRequest;
import com.bookingapp.web.dto.UpdateBookingRequest;
import com.bookingapp.web.mapper.BookingWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
@Tag(name = "Bookings", description = "Booking operations for customers and admins")
public class BookingController {

    private final BookingService bookingService;
    private final BookingWebMapper bookingWebMapper;

    public BookingController(
            BookingService bookingService,
            BookingWebMapper bookingWebMapper
    ) {
        this.bookingService = bookingService;
        this.bookingWebMapper = bookingWebMapper;
    }

    @PostMapping
    @Operation(summary = "Create booking", security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        Booking createdBooking = bookingService.createBooking(
                request.accommodationId(),
                request.checkInDate(),
                request.checkOutDate()
        );
        return bookingWebMapper.toResponse(createdBooking);
    }

    @GetMapping
    @Operation(summary = "List bookings for current user or all bookings for admin",
            security = @SecurityRequirement(name = "bearerAuth"))
    public List<BookingResponse> listBookings(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "status", required = false) BookingStatus status
    ) {
        return bookingService.listBookings(bookingWebMapper.toFilterQuery(userId, status)).stream()
                .map(bookingWebMapper::toResponse)
                .toList();
    }

    @GetMapping("/my")
    @Operation(summary = "List current user's bookings",
            security = @SecurityRequirement(name = "bearerAuth"))
    public List<BookingResponse> listMyBookings() {
        return bookingService.listMyBookings().stream()
                .map(bookingWebMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by id", security = @SecurityRequirement(name = "bearerAuth"))
    public BookingDetailResponse getBookingById(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id);
        Accommodation accommodation = bookingService.getAccommodationByBookingId(id);
        return bookingWebMapper.toDetailResponse(booking, accommodation);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookingRequest request
    ) {
        Booking updatedBooking = bookingService.updateBooking(id,
                request.checkInDate(), request.checkOutDate());
        return bookingWebMapper.toResponse(updatedBooking);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse patchBooking(
            @PathVariable Long id,
            @Valid @RequestBody PatchBookingRequest request
    ) {
        Booking currentBooking = bookingService.getBookingById(id);
        Booking updatedBooking = bookingService.updateBooking(
                id,
                request.checkInDate() != null ? request.checkInDate() : currentBooking
                        .getCheckInDate(),
                request.checkOutDate() != null ? request.checkOutDate() : currentBooking
                        .getCheckOutDate()
        );
        return bookingWebMapper.toResponse(updatedBooking);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel booking", security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse cancelBooking(@PathVariable Long id) {
        Booking canceledBooking = bookingService.cancelBooking(id);
        return bookingWebMapper.toResponse(canceledBooking);
    }
}

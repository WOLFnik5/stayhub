package com.bookingapp.web.controller;

import com.bookingapp.domain.model.enums.BookingStatus;
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

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingWebMapper bookingWebMapper;

    @PostMapping
    @Operation(summary = "Create booking", security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingWebMapper.toResponse(bookingService.createBooking(request));
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
    public BookingDetailResponse getBookingById(@PathVariable("id") Long id) {
        return bookingWebMapper.toDetailResponse(bookingService.getBookingDetail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse updateBooking(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateBookingRequest request
    ) {
        return bookingWebMapper.toResponse(bookingService.updateBooking(id, request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse patchBooking(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchBookingRequest request
    ) {
        return bookingWebMapper.toResponse(bookingService.patchBooking(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel booking", security = @SecurityRequirement(name = "bearerAuth"))
    public BookingResponse cancelBooking(@PathVariable("id") Long id) {
        return bookingWebMapper.toResponse(bookingService.cancelBooking(id));
    }
}

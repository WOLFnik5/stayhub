package com.bookingapp.web.controller;

import com.bookingapp.domain.model.enums.BookingStatus;
import com.bookingapp.web.dto.BookingDetailResponse;
import com.bookingapp.web.dto.BookingResponse;
import com.bookingapp.web.dto.CreateBookingRequest;
import com.bookingapp.web.dto.PatchBookingRequest;
import com.bookingapp.web.dto.UpdateBookingRequest;
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

@RequestMapping("/bookings")
@Tag(name = "Bookings", description = "Booking operations for customers and admins")
public interface BookingApi {

    @PostMapping
    @Operation(summary = "Create booking", security = @SecurityRequirement(name = "bearerAuth"))
    BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request);

    @GetMapping
    @Operation(summary = "List bookings for current user or all bookings for admin",
            security = @SecurityRequirement(name = "bearerAuth"))
    List<BookingResponse> listBookings(
            @RequestParam(name = "user_id", required = false) Long userId,
            @RequestParam(name = "status", required = false) BookingStatus status
    );

    @GetMapping("/my")
    @Operation(summary = "List current user's bookings",
            security = @SecurityRequirement(name = "bearerAuth"))
    List<BookingResponse> listMyBookings();

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by id", security = @SecurityRequirement(name = "bearerAuth"))
    BookingDetailResponse getBookingById(@PathVariable("id") Long id);

    @PutMapping("/{id}")
    @Operation(summary = "Replace booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    BookingResponse updateBooking(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateBookingRequest request
    );

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update booking dates",
            security = @SecurityRequirement(name = "bearerAuth"))
    BookingResponse patchBooking(
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchBookingRequest request
    );

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel booking", security = @SecurityRequirement(name = "bearerAuth"))
    BookingResponse cancelBooking(@PathVariable("id") Long id);
}

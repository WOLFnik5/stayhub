package com.bookingapp.web.dto;

import com.bookingapp.domain.model.Accommodation;
import com.bookingapp.domain.model.Booking;

public record BookingDetail(
        Booking booking,
        Accommodation accommodation
) {
}

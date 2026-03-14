package com.bookingapp.application.model;

import java.time.LocalDate;

public record ExpireBookingsCommand(
        LocalDate businessDate
) {
}

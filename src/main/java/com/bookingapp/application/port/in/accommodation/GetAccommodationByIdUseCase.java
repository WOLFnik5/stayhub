package com.bookingapp.application.port.in.accommodation;

import com.bookingapp.domain.model.Accommodation;

public interface GetAccommodationByIdUseCase {

    Accommodation getAccommodationById(Long accommodationId);
}

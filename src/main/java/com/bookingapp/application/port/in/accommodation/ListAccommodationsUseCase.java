package com.bookingapp.application.port.in.accommodation;

import com.bookingapp.domain.model.Accommodation;

import java.util.List;

public interface ListAccommodationsUseCase {

    List<Accommodation> listAccommodations();
}

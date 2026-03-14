package com.bookingapp.application.port.in.accommodation;

import com.bookingapp.application.model.UpdateAccommodationCommand;
import com.bookingapp.domain.model.Accommodation;

public interface UpdateAccommodationUseCase {

    Accommodation updateAccommodation(UpdateAccommodationCommand command);
}

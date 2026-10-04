package com.bookingapp.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconcilePaymentRequest(@NotBlank @Size(max = 255) String sessionId) {
}

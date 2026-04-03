package com.bookingapp.service.validation;

import com.bookingapp.exception.BusinessValidationException;

public final class TextValidationUtils {

    private TextValidationUtils() {
    }

    public static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(message);
        }
        return value.trim();
    }

    public static String selectNonBlank(String candidate, String fallback, String fieldName) {
        if (candidate == null) {
            return fallback;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessValidationException(
                    "Field '" + fieldName + "' must not be blank"
            );
        }

        return trimmed;
    }
}

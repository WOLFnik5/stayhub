package com.bookingapp.service.validation;

import java.util.Locale;

public final class EmailNormalizationUtils {

    private EmailNormalizationUtils() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

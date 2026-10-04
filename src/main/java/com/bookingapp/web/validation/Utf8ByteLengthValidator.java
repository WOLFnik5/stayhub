package com.bookingapp.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {
    private int max;

    @Override
    public void initialize(Utf8ByteLength constraint) {
        max = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Nullability is controlled separately by @NotBlank/@NotNull.
        return value == null || value.length() <= max
                && value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}

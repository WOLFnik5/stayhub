package com.bookingapp.web.dto;

import com.bookingapp.domain.model.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}

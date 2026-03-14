package com.bookingapp.adapter.in.web.user;

import com.bookingapp.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull UserRole role
) {
}

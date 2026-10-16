package com.releasepilot.dto;

import com.releasepilot.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "username must not be empty")
        @Size(max = 50, message = "username must be at most 50 characters")
        String username,

        @NotBlank(message = "password must not be empty")
        @Size(max = 128, message = "password must be at most 128 characters")
        String password,

        @NotNull(message = "role is required (ADMIN or APPROVER)")
        Role role
) {
}
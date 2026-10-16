package com.releasepilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword must not be empty")
        @Size(max = 128, message = "currentPassword must be at most 128 characters")
        String currentPassword,

        @NotBlank(message = "newPassword must not be empty")
        @Size(max = 128, message = "newPassword must be at most 128 characters")
        String newPassword
) {
}
package com.releasepilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "password must not be empty")
        @Size(max = 128, message = "password must be at most 128 characters")
        String password
) {
}
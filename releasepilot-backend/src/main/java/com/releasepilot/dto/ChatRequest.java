package com.releasepilot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        @NotBlank(message = "message must not be empty")
        @Size(max = 4000, message = "message must be under 4000 characters")
        String message,

        @DecimalMin(value = "0.0", message = "temperature must be >= 0.0")
        @DecimalMax(value = "2.0", message = "temperature must be <= 2.0")
        Double temperature
) {
}
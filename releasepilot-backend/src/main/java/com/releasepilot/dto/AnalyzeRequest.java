package com.releasepilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(

        @NotBlank(message = "text must not be empty")
        @Size(max = 6000, message = "text must be under 6000 characters")
        String text
) {
}
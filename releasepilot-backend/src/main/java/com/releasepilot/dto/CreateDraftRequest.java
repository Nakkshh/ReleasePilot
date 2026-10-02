package com.releasepilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateDraftRequest(
        @NotBlank
        @Pattern(regexp = "v\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z]+(\\.[0-9A-Za-z]+)*)?",
                message = "must look like v1.2.3 or v1.2.3-rc.1")
        String tag,
        Boolean prerelease
) {
}
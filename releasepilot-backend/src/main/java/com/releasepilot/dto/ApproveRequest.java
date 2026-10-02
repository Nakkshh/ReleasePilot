package com.releasepilot.dto;

import jakarta.validation.constraints.Size;

public record ApproveRequest(
        @Size(max = 100) String decidedBy,
        @Size(max = 1000) String comment,
        Boolean override,
        @Size(max = 1000) String overrideReason,
        @Size(max = 300) String title
) {
}
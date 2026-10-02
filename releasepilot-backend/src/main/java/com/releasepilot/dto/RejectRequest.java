package com.releasepilot.dto;

import jakarta.validation.constraints.Size;

public record RejectRequest(
        @Size(max = 100) String decidedBy,
        @Size(max = 1000) String comment
) {
}
package com.releasepilot.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ChangeAnalysis(

        @JsonPropertyDescription("One or two sentence plain-English summary of the change")
        String summary,

        @JsonPropertyDescription("One of: FEATURE, BUG_FIX, BREAKING_CHANGE, DOCS, CHORE, REFACTOR")
        String changeType,

        @JsonPropertyDescription("One of: LOW, MEDIUM, HIGH — how risky this change is to release")
        String riskLevel
) {
}
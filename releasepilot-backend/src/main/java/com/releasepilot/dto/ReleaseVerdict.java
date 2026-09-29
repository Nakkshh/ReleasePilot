package com.releasepilot.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ReleaseVerdict(
        @JsonPropertyDescription("One of: READY, NOT_READY, UNKNOWN")
        String status,

        @JsonPropertyDescription("One or two sentence summary of the decision")
        String summary,

        @JsonPropertyDescription("Short factual reasons supporting the decision, each taken from tool results")
        List<String> reasons,

        @JsonPropertyDescription("Concrete blockers that must be fixed before release; empty list if none")
        List<String> blockers
) {
}
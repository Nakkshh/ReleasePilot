package com.releasepilot.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record ReleaseNotes(
        @JsonPropertyDescription("Short release title, e.g. 'Release notes' plus the main theme")
        String title,
        @JsonPropertyDescription("One or two sentence summary of what changed, based only on the listed pull requests")
        String summary,
        @JsonPropertyDescription("New user-facing capabilities. Each item formatted as '#12 Title (@author)'")
        List<String> features,
        @JsonPropertyDescription("Bug fixes. Each item formatted as '#12 Title (@author)'")
        List<String> fixes,
        @JsonPropertyDescription("Docs, refactors, tests, CI and other maintenance. Each item formatted as '#12 Title (@author)'")
        List<String> chores,
        @JsonPropertyDescription("Breaking changes only; empty list if none. Each item formatted as '#12 Title (@author)'")
        List<String> breakingChanges
) {
}
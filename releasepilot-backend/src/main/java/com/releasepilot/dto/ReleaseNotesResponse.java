package com.releasepilot.dto;

import java.time.Instant;
import java.util.List;

/**
 * baseline = "since v0.1.0 (published ...)" or "last N merged pull requests (no previous release)".
 * invalidReferences = PR numbers the model wrote that are not in the evidence (should be empty).
 * omittedPrs = evidence PR numbers the model left out of every category (should be empty).
 */
public record ReleaseNotesResponse(
        ReleaseNotes notes,
        String baseline,
        int prCount,
        List<Integer> invalidReferences,
        List<Integer> omittedPrs,
        int modelCalls,
        long elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        Instant timestamp
) {
}
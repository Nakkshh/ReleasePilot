package com.releasepilot.dto;

import java.time.Instant;
import java.util.List;

public record ReleaseCheckResponse(
        ReleaseVerdict verdict,
        List<TraceStep> trace,
        int modelCalls,
        int toolRounds,
        long elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        Instant timestamp
) {
}
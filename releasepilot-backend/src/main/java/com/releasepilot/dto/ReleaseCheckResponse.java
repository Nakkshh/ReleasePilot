package com.releasepilot.dto;

import java.time.Instant;
import java.util.List;

/**
 * mode = "agent" (model chose the tools) or "fallback" (Java gathered the evidence, one model call decided).
 * note = why the fallback was used; null in agent mode.
 * elapsedMs = total wall time of the request, including failed attempts.
 * Token counts cover only the successful model run (failed attempts are not counted).
 */
public record ReleaseCheckResponse(
        ReleaseVerdict verdict,
        String mode,
        String note,
        List<TraceStep> trace,
        int modelCalls,
        int toolRounds,
        long elapsedMs,
        Integer promptTokens,
        Integer completionTokens,
        Instant timestamp
) {
    public ReleaseCheckResponse withElapsedMs(long ms) {
        return new ReleaseCheckResponse(verdict, mode, note, trace, modelCalls, toolRounds,
                ms, promptTokens, completionTokens, timestamp);
    }
}
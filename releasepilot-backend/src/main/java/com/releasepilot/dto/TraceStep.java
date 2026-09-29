package com.releasepilot.dto;

/**
 * One tool call made by the agent.
 * modelMs = duration of the LLM call that requested the tool(s).
 * toolRoundMs = time from that response until the next LLM call started, i.e. tool execution
 * for the whole round (shared by parallel calls); null if the run ended before the next call.
 */
public record TraceStep(
        int round,
        String tool,
        String arguments,
        long modelMs,
        Long toolRoundMs
) {
}
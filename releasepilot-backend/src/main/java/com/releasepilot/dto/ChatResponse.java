package com.releasepilot.dto;

import java.time.Instant;

public record ChatResponse(
        String reply,
        Instant timestamp,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
    public static ChatResponse of(String reply, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new ChatResponse(reply, Instant.now(), promptTokens, completionTokens, totalTokens);
    }
}
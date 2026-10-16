package com.releasepilot.agent;

import com.openai.errors.RateLimitException;

import java.util.List;
import java.util.OptionalLong;

/** Finds the OpenAI SDK's 429 exception anywhere in a cause chain and reads its Retry-After. */
public final class LlmRateLimit {

    private static final int MAX_DEPTH = 10;
    private static final long MAX_RETRY_AFTER_SECONDS = 3600;

    private LlmRateLimit() {
    }

    public static boolean isRateLimited(Throwable t) {
        return find(t) != null;
    }

    /** The first RateLimitException in the cause chain, or null. */
    public static RateLimitException find(Throwable t) {
        int depth = 0;
        while (t != null && depth++ < MAX_DEPTH) {
            if (t instanceof RateLimitException r) {
                return r;
            }
            t = t.getCause();
        }
        return null;
    }

    /** Retry-After in whole seconds (rounded up, 1..3600). Empty if absent or not a number (e.g. an HTTP date). */
    public static OptionalLong retryAfterSeconds(Throwable t) {
        RateLimitException r = find(t);
        if (r == null) {
            return OptionalLong.empty();
        }
        List<String> values = r.headers().values("retry-after");
        if (values.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            double seconds = Double.parseDouble(values.get(0).strip());
            if (Double.isNaN(seconds) || seconds < 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.min(MAX_RETRY_AFTER_SECONDS, Math.max(1L, (long) Math.ceil(seconds))));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
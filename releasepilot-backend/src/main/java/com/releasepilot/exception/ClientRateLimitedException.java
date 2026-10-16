package com.releasepilot.exception;

/** Our own limiter said no (per-client, global, or too many concurrent agent runs). Maps to HTTP 429. */
public class ClientRateLimitedException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public ClientRateLimitedException(String code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
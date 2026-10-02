package com.releasepilot.exception;

/** A request that is valid but conflicts with current state. Maps to HTTP 409 with a machine-readable code. */
public class DraftConflictException extends RuntimeException {

    private final String code;

    public DraftConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
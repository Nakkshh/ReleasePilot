package com.releasepilot.exception;

/** A malformed request that is not covered by bean validation. Maps to HTTP 400 with a machine-readable code. */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
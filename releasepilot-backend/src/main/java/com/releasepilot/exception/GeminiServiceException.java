package com.releasepilot.exception;

public class GeminiServiceException extends RuntimeException {

    public GeminiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
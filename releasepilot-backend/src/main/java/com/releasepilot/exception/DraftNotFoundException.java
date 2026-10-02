package com.releasepilot.exception;

public class DraftNotFoundException extends RuntimeException {

    public DraftNotFoundException(long id) {
        super("Draft " + id + " was not found.");
    }
}
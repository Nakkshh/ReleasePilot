package com.releasepilot.auth;

import com.releasepilot.exception.AuthException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class PasswordPolicy {

    static final int MIN_CHARS = 12;
    static final int MAX_BYTES = 72;   // bcrypt ignores (or rejects) anything beyond this

    private PasswordPolicy() {
    }

    static void check(String username, String password) {
        if (password == null || password.length() < MIN_CHARS) {
            throw weak("Password must be at least " + MIN_CHARS + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw weak("Password is too long (at most " + MAX_BYTES + " bytes; non-ASCII characters count for more).");
        }
        if (password.isBlank()) {
            throw weak("Password must not be blank.");
        }
        if (password.chars().distinct().count() < 4) {
            throw weak("Password must contain at least 4 different characters.");
        }
        String user = username == null ? "" : username.toLowerCase(Locale.ROOT);
        if (!user.isEmpty() && password.toLowerCase(Locale.ROOT).contains(user)) {
            throw weak("Password must not contain the username.");
        }
    }

    private static AuthException weak(String message) {
        return new AuthException("WEAK_PASSWORD", message, HttpStatus.BAD_REQUEST);
    }
}
package com.releasepilot.config;

import com.releasepilot.exception.AdminAuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenInterceptor.class);
    static final String HEADER = "X-Admin-Token";
    private static final int MIN_LENGTH = 16;

    private final byte[] expectedHash;   // null when not configured

    public AdminTokenInterceptor(@Value("${release.admin-token:}") String token) {
        if (token == null || token.strip().length() < MIN_LENGTH) {
            this.expectedHash = null;
            log.warn("RELEASEPILOT_ADMIN_TOKEN is missing or shorter than {} characters. "
                    + "All /api/release/** requests will be refused (503) until it is set.", MIN_LENGTH);
        } else {
            this.expectedHash = sha256(token.strip());
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;   // CORS preflight carries no custom headers
        }
        if (expectedHash == null) {
            throw new AdminAuthException("ADMIN_TOKEN_NOT_CONFIGURED",
                    "The server has no admin token configured. Set RELEASEPILOT_ADMIN_TOKEN (16+ characters) and restart.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(sha256(given.strip()), expectedHash)) {
            throw new AdminAuthException("UNAUTHORIZED",
                    "Missing or invalid " + HEADER + " header.", HttpStatus.UNAUTHORIZED);
        }
        return true;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
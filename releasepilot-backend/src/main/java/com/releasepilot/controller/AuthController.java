package com.releasepilot.controller;

import com.releasepilot.auth.AuthService;
import com.releasepilot.dto.LoginRequest;
import com.releasepilot.dto.LoginResponse;
import com.releasepilot.exception.ClientRateLimitedException;
import com.releasepilot.ratelimit.TokenBucketLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenBucketLimiter attempts;   // null = disabled

    public AuthController(AuthService authService,
                          @Value("${auth.login-attempts-per-minute:10}") int attemptsPerMinute) {
        this.authService = authService;
        this.attempts = attemptsPerMinute > 0
                ? TokenBucketLimiter.perMinute(attemptsPerMinute, 10_000, System::nanoTime) : null;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (attempts != null) {
            TokenBucketLimiter.Decision d = attempts.tryAcquire(http.getRemoteAddr());
            if (!d.allowed()) {
                throw new ClientRateLimitedException("RATE_LIMITED",
                        "Too many login attempts. Try again in " + d.retryAfterSeconds() + " seconds.",
                        d.retryAfterSeconds());
            }
        }
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return Map.of("username", jwt.getSubject(), "roles", roles == null ? List.of() : roles);
    }
}
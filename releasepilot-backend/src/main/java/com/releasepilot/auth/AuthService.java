package com.releasepilot.auth;

import com.releasepilot.dto.LoginResponse;
import com.releasepilot.exception.AuthException;
import com.releasepilot.model.AppUser;
import com.releasepilot.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final String dummyHash;   // checked when the user does not exist, so timing doesn't reveal usernames

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public LoginResponse login(String rawUsername, String password) {
        if (!jwt.isConfigured()) {
            throw new AuthException("AUTH_NOT_CONFIGURED",
                    "Login is not configured on this server. Set RELEASEPILOT_JWT_SECRET (32+ characters) and restart.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        String username = rawUsername == null ? "" : rawUsername.strip().toLowerCase(Locale.ROOT);
        AppUser user = users.findByUsername(username).orElse(null);

        boolean passwordOk = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !user.isEnabled() || !passwordOk) {
            log.warn("[AUTH] failed login attempt");
            throw new AuthException("INVALID_CREDENTIALS", "Invalid username or password.", HttpStatus.UNAUTHORIZED);
        }

        JwtService.IssuedToken issued = jwt.issue(user.getUsername(), user.getRole());
        log.info("[AUTH] login ok: {}", user.getUsername());
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(), user.getUsername(), issued.roles());
    }
}

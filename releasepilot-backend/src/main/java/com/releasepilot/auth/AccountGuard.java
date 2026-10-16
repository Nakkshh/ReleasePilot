package com.releasepilot.auth;

import com.releasepilot.model.AppUser;
import com.releasepilot.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs after a token's signature and expiry are verified. One indexed lookup per authenticated request:
 * the user must still exist and be enabled, the token must not predate the last password change,
 * and the roles come from the database (the "roles" claim in the token is replaced).
 */
@Component
public class AccountGuard {

    private final AppUserRepository users;
    private final boolean enabled;

    public AccountGuard(AppUserRepository users,
                        @Value("${auth.verify-user-on-request:true}") boolean enabled) {
        this.users = users;
        this.enabled = enabled;
    }

    public Jwt verify(Jwt jwt) {
        if (!enabled) {
            return jwt;
        }
        String username = jwt.getSubject();
        AppUser user = username == null ? null : users.findByUsername(username).orElse(null);
        if (user == null || !user.isEnabled()) {
            throw new BadJwtException("Account is disabled or no longer exists.");
        }
        // JWT iat has whole-second precision, so compare at that precision.
        if (jwt.getIssuedAt() == null
                || jwt.getIssuedAt().isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS))) {
            throw new BadJwtException("Token was issued before the last password change.");
        }
        List<String> roles = JwtService.rolesOf(user.getRole());
        return Jwt.withTokenValue(jwt.getTokenValue())
                .headers(h -> h.putAll(jwt.getHeaders()))
                .claims(c -> {
                    c.putAll(jwt.getClaims());
                    c.put("roles", roles);
                })
                .build();
    }
}
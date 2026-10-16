package com.releasepilot.auth;

import com.releasepilot.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Issues and verifies HS256 JWTs. Without a configured secret it uses a random key, so nothing it signs can be reused. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    static final int MIN_SECRET_CHARS = 32;
    private static final long MIN_TTL_MINUTES = 1;
    private static final long MAX_TTL_MINUTES = 24 * 60;

    public record IssuedToken(String token, Instant expiresAt, List<String> roles) {
    }

    private final boolean configured;
    private final Duration ttl;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    @Autowired
    public JwtService(@Value("${auth.jwt-secret:}") String secret,
                      @Value("${auth.token-ttl-minutes:60}") long ttlMinutes) {
        this(secret, ttlMinutes, Clock.systemUTC());
    }

    /** Test seam: lets tests issue tokens from a different point in time. */
    JwtService(String secret, long ttlMinutes, Clock clock) {
        String s = secret == null ? "" : secret.strip();
        byte[] keyBytes;
        if (s.length() >= MIN_SECRET_CHARS) {
            keyBytes = s.getBytes(StandardCharsets.UTF_8);
            this.configured = true;
        } else {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            this.configured = false;
            log.warn("RELEASEPILOT_JWT_SECRET is missing or shorter than {} characters. "
                    + "Login is disabled (503) and no token will verify until it is set.", MIN_SECRET_CHARS);
        }
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.ttl = Duration.ofMinutes(Math.min(MAX_TTL_MINUTES, Math.max(MIN_TTL_MINUTES, ttlMinutes)));
        this.clock = clock;
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public boolean isConfigured() {
        return configured;
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public IssuedToken issue(String username, Role role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        List<String> roles = rolesOf(role);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, roles);
    }

    /** ADMIN carries APPROVER too, so rules can say hasRole("APPROVER") without a role hierarchy. */
    static List<String> rolesOf(Role role) {
        return role == Role.ADMIN ? List.of("ADMIN", "APPROVER") : List.of("APPROVER");
    }
}
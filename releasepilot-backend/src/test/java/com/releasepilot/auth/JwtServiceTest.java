package com.releasepilot.auth;

import com.releasepilot.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";
    private static final String OTHER_SECRET = "another-secret-9876543210-zyxwvutsrqpo";

    @Test
    void adminTokenRoundTripsWithBothRoles() {
        JwtService jwt = new JwtService(SECRET, 60);
        JwtService.IssuedToken issued = jwt.issue("alice", Role.ADMIN);

        Jwt decoded = jwt.decoder().decode(issued.token());

        assertThat(decoded.getSubject()).isEqualTo("alice");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN", "APPROVER");
        assertThat(decoded.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(55)));
    }

    @Test
    void approverTokenCarriesOnlyTheApproverRole() {
        JwtService jwt = new JwtService(SECRET, 60);
        Jwt decoded = jwt.decoder().decode(jwt.issue("bob", Role.APPROVER).token());

        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("APPROVER");
    }

    @Test
    void anExpiredTokenIsRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService old = new JwtService(SECRET, 60, twoHoursAgo);   // expired an hour ago
        String token = old.issue("alice", Role.ADMIN).token();

        assertThatThrownBy(() -> new JwtService(SECRET, 60).decoder().decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String forged = new JwtService(OTHER_SECRET, 60).issue("alice", Role.ADMIN).token();

        assertThatThrownBy(() -> new JwtService(SECRET, 60).decoder().decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aMissingOrShortSecretMeansNotConfigured_andItsTokensNeverVerify() {
        assertThat(new JwtService("", 60).isConfigured()).isFalse();
        assertThat(new JwtService("too-short", 60).isConfigured()).isFalse();
        assertThat(new JwtService(SECRET, 60).isConfigured()).isTrue();

        String token = new JwtService("", 60).issue("alice", Role.ADMIN).token();
        assertThatThrownBy(() -> new JwtService(SECRET, 60).decoder().decode(token))
                .isInstanceOf(JwtException.class);
    }
}
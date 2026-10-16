package com.releasepilot.auth;

import com.releasepilot.exception.AuthException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static void assertWeak(String username, String password) {
        assertThatThrownBy(() -> PasswordPolicy.check(username, password))
                .isInstanceOfSatisfying(AuthException.class,
                        e -> assertThat(e.getCode()).isEqualTo("WEAK_PASSWORD"));
    }

    @Test
    void acceptsAGoodPassword() {
        assertThatCode(() -> PasswordPolicy.check("alice", "correct-horse-battery")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortPasswords() {
        assertWeak("alice", "short-pw-1");
        assertWeak("alice", null);
    }

    @Test
    void rejectsMoreThan72Bytes() {
        assertWeak("alice", "éèêëàâäôöùûüç".repeat(6));   // 78 characters, 156 bytes
    }

    @Test
    void rejectsLowVarietyAndBlank() {
        assertWeak("alice", "aaaaaaaaaaaa");
        assertWeak("alice", "            ");
    }

    @Test
    void rejectsPasswordsContainingTheUsername() {
        assertWeak("alice", "My-ALICE-passphrase");
    }
}
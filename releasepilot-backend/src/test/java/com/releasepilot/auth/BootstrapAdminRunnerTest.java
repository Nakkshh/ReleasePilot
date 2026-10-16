package com.releasepilot.auth;

import com.releasepilot.model.AppUser;
import com.releasepilot.model.Role;
import com.releasepilot.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminRunnerTest {

    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void createsTheFirstAdminWithAHashedPassword() {
        when(repo.count()).thenReturn(0L);

        new BootstrapAdminRunner(repo, encoder, "  Admin ", "a-long-password-123").bootstrap();

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("a-long-password-123");
        assertThat(encoder.matches("a-long-password-123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWhenUsersAlreadyExist() {
        when(repo.count()).thenReturn(1L);

        new BootstrapAdminRunner(repo, encoder, "admin", "a-long-password-123").bootstrap();

        verify(repo, never()).save(any());
    }

    @Test
    void refusesAShortPasswordAndMissingCredentials() {
        when(repo.count()).thenReturn(0L);

        new BootstrapAdminRunner(repo, encoder, "admin", "short").bootstrap();
        new BootstrapAdminRunner(repo, encoder, "", "").bootstrap();

        verify(repo, never()).save(any());
    }

    @Test
    void refusesAnInvalidUsername() {
        when(repo.count()).thenReturn(0L);

        new BootstrapAdminRunner(repo, encoder, "not valid!", "a-long-password-123").bootstrap();

        verify(repo, never()).save(any());
    }
}
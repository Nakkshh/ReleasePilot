package com.releasepilot.auth;

import com.releasepilot.controller.AuthController;
import com.releasepilot.dto.LoginRequest;
import com.releasepilot.dto.LoginResponse;
import com.releasepilot.exception.ClientRateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginAttemptsAreRateLimitedPerClient() {
        AuthService service = mock(AuthService.class);
        when(service.login(anyString(), anyString()))
                .thenReturn(new LoginResponse("t", "Bearer", Instant.now(), "alice", List.of("APPROVER")));
        AuthController controller = new AuthController(service, 2);

        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("9.9.9.9");
        LoginRequest body = new LoginRequest("alice", "whatever-password");

        controller.login(body, http);
        controller.login(body, http);

        assertThatThrownBy(() -> controller.login(body, http))
                .isInstanceOf(ClientRateLimitedException.class)
                .extracting("code").isEqualTo("RATE_LIMITED");
    }
}
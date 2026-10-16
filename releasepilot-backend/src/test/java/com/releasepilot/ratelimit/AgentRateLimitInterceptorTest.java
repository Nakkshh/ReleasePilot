package com.releasepilot.ratelimit;

import com.releasepilot.dto.ErrorResponse;
import com.releasepilot.exception.ClientRateLimitedException;
import com.releasepilot.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRateLimitInterceptorTest {

    private static final Object HANDLER = new Object();

    private static MockHttpServletRequest request(String method, String remoteAddr) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, "/api/agent/ask");
        r.setRemoteAddr(remoteAddr);
        return r;
    }

    private static boolean pre(AgentRateLimitInterceptor i, MockHttpServletRequest r) {
        return i.preHandle(r, new MockHttpServletResponse(), HANDLER);
    }

    private static void done(AgentRateLimitInterceptor i, MockHttpServletRequest r) {
        i.afterCompletion(r, new MockHttpServletResponse(), HANDLER, null);
    }

    @Test
    void perClientLimitRejectsTheExtraRequestWithRateLimited() {
        var i = new AgentRateLimitInterceptor(true, 2, 0, 0, () -> 0L);   // clock frozen: no refill
        assertThat(pre(i, request("POST", "1.1.1.1"))).isTrue();
        assertThat(pre(i, request("POST", "1.1.1.1"))).isTrue();

        assertThatThrownBy(() -> pre(i, request("POST", "1.1.1.1")))
                .isInstanceOf(ClientRateLimitedException.class)
                .satisfies(e -> {
                    var ex = (ClientRateLimitedException) e;
                    assertThat(ex.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(ex.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    void clientsHaveSeparateBudgets() {
        var i = new AgentRateLimitInterceptor(true, 1, 0, 0, () -> 0L);
        assertThat(pre(i, request("POST", "1.1.1.1"))).isTrue();
        assertThat(pre(i, request("POST", "2.2.2.2"))).isTrue();
        assertThatThrownBy(() -> pre(i, request("POST", "1.1.1.1")))
                .isInstanceOf(ClientRateLimitedException.class);
    }

    @Test
    void globalLimitAppliesAcrossClients() {
        var i = new AgentRateLimitInterceptor(true, 0, 2, 0, () -> 0L);
        assertThat(pre(i, request("POST", "1.1.1.1"))).isTrue();
        assertThat(pre(i, request("POST", "2.2.2.2"))).isTrue();
        assertThatThrownBy(() -> pre(i, request("POST", "3.3.3.3")))
                .isInstanceOf(ClientRateLimitedException.class)
                .extracting("code").isEqualTo("RATE_LIMITED");
    }

    @Test
    void concurrencyCapIsReleasedWhenARequestCompletes() {
        var i = new AgentRateLimitInterceptor(true, 0, 0, 1, () -> 0L);
        MockHttpServletRequest first = request("POST", "1.1.1.1");
        assertThat(pre(i, first)).isTrue();

        assertThatThrownBy(() -> pre(i, request("POST", "2.2.2.2")))
                .isInstanceOf(ClientRateLimitedException.class)
                .extracting("code").isEqualTo("AGENT_BUSY");

        done(i, first);
        assertThat(pre(i, request("POST", "2.2.2.2"))).isTrue();
    }

    @Test
    void aRejectedRequestNeverFreesSomeoneElsesSlot() {
        var i = new AgentRateLimitInterceptor(true, 0, 0, 1, () -> 0L);
        MockHttpServletRequest holder = request("POST", "1.1.1.1");
        assertThat(pre(i, holder)).isTrue();

        MockHttpServletRequest rejected = request("POST", "2.2.2.2");
        assertThatThrownBy(() -> pre(i, rejected)).isInstanceOf(ClientRateLimitedException.class);
        done(i, rejected);   // must not release the holder's slot

        assertThatThrownBy(() -> pre(i, request("POST", "3.3.3.3")))
                .isInstanceOf(ClientRateLimitedException.class);
    }

    @Test
    void optionsPreflightAndDisabledModeAreNeverLimited() {
        var limited = new AgentRateLimitInterceptor(true, 1, 1, 1, () -> 0L);
        for (int n = 0; n < 5; n++) {
            assertThat(pre(limited, request("OPTIONS", "1.1.1.1"))).isTrue();
        }

        var disabled = new AgentRateLimitInterceptor(false, 1, 1, 1, () -> 0L);
        for (int n = 0; n < 5; n++) {
            assertThat(pre(disabled, request("POST", "1.1.1.1"))).isTrue();
        }
    }

    @Test
    void handlerMapsItTo429WithRetryAfter() {
        ResponseEntity<ErrorResponse> res = new GlobalExceptionHandler()
                .handleClientRateLimited(new ClientRateLimitedException("RATE_LIMITED", "slow down", 7));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(res.getBody().code()).isEqualTo("RATE_LIMITED");
    }
}
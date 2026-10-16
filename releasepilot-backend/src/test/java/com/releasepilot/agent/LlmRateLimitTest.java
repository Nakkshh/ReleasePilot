package com.releasepilot.agent;

import com.openai.core.http.Headers;
import com.openai.errors.RateLimitException;
import com.releasepilot.dto.ErrorResponse;
import com.releasepilot.exception.GeminiServiceException;
import com.releasepilot.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRateLimitTest {

    private static RateLimitException rateLimit(String retryAfter) {
        Headers.Builder h = Headers.builder();
        if (retryAfter != null) {
            h.put("Retry-After", retryAfter);
        }
        return RateLimitException.builder().headers(h.build()).build();
    }

    @Test
    void findsTheExceptionInANestedCauseChain() {
        Throwable wrapped = new GeminiServiceException("outer", new RuntimeException("mid", rateLimit("5")));
        assertThat(LlmRateLimit.isRateLimited(wrapped)).isTrue();
        assertThat(LlmRateLimit.isRateLimited(new RuntimeException("429: nope"))).isFalse();
        assertThat(LlmRateLimit.isRateLimited(null)).isFalse();
    }

    @Test
    void retryAfterIsParsedRoundedUpAndClamped() {
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit("12")).getAsLong()).isEqualTo(12);
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit("2.3")).getAsLong()).isEqualTo(3);
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit("0")).getAsLong()).isEqualTo(1);
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit("999999")).getAsLong()).isEqualTo(3600);
    }

    @Test
    void missingOrNonNumericRetryAfterIsEmpty() {
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit(null))).isEmpty();
        assertThat(LlmRateLimit.retryAfterSeconds(rateLimit("Wed, 21 Oct 2026 07:28:00 GMT"))).isEmpty();
        assertThat(LlmRateLimit.retryAfterSeconds(new RuntimeException("x"))).isEmpty();
    }

    @Test
    void aRateLimitIsNeverAProviderGlitch() {
        assertThat(ProviderGlitch.matches(new GeminiServiceException("x", rateLimit("5")))).isFalse();
    }

    @Test
    void handlerMapsAWrappedRateLimitTo429WithRetryAfter() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> res =
                handler.handleGeminiFailure(new GeminiServiceException("Release check failed", rateLimit("12")));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
        assertThat(res.getBody().code()).isEqualTo("LLM_RATE_LIMITED");
        assertThat(res.getBody().message()).contains("12 seconds");
    }

    @Test
    void catchAllHandlerAlsoMapsAnUnwrappedRateLimit_andOtherErrorsStay500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> limited = handler.handleUnexpected(rateLimit(null));
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
        assertThat(handler.handleUnexpected(new RuntimeException("boom")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
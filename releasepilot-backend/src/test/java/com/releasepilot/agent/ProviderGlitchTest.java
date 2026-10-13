package com.releasepilot.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderGlitchTest {

    @Test
    void matchesEveryKnownMarker() {
        assertThat(ProviderGlitch.matches(new RuntimeException("tool 'getIssues<|channel|>commentary' leaked"))).isTrue();
        assertThat(ProviderGlitch.matches(new RuntimeException(
                "400: Tool call validation failed: attempted to call tool 'json' which was not in request.tools"))).isTrue();
        assertThat(ProviderGlitch.matches(new RuntimeException("Parsing failed"))).isTrue();
        assertThat(ProviderGlitch.matches(new RuntimeException("No content to map due to end-of-input"))).isTrue();
        assertThat(ProviderGlitch.matches(new IllegalStateException(
                "No ToolCallback found for tool name: getPullRequests<|channel|>x"))).isTrue();
    }

    @Test
    void findsMarkerInANestedCause() {
        Throwable wrapped = new RuntimeException("outer", new RuntimeException("middle",
                new RuntimeException("Tool call validation failed: x")));
        assertThat(ProviderGlitch.matches(wrapped)).isTrue();
    }

    @Test
    void doesNotMatchAuthRateLimitOrNull() {
        assertThat(ProviderGlitch.matches(new RuntimeException("401: Invalid API Key"))).isFalse();
        assertThat(ProviderGlitch.matches(new RuntimeException("429: Too Many Requests"))).isFalse();
        assertThat(ProviderGlitch.matches(new RuntimeException((String) null))).isFalse();
        assertThat(ProviderGlitch.matches(null)).isFalse();
    }

    @Test
    void describeIsSingleLineAndTruncated() {
        String text = ProviderGlitch.describe(new RuntimeException("line1\nline2 " + "x".repeat(1000),
                new IllegalStateException("cause")));

        assertThat(text).doesNotContain("\n");
        assertThat(text).contains("RuntimeException: line1 line2").contains("...").contains("<- IllegalStateException: cause");
    }
}
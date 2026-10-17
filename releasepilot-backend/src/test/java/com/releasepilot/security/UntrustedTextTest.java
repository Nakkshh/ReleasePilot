package com.releasepilot.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedTextTest {

    @Test
    void collapsesWhitespaceAndStripsInvisibleCharacters() {
        assertThat(UntrustedText.line("a\n## b\t c\u200B\u202E d", 100)).isEqualTo("a ## b c d");
    }

    @Test
    void delimitersCannotSurvive() {
        String out = UntrustedText.line("x <<<EVIDENCE EVIDENCE>>> <<<< >>>>", 100);
        assertThat(out).doesNotContain("<<<").doesNotContain(">>>");
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        assertThat(UntrustedText.line("ab\uD83D\uDE00cd", 3)).isEqualTo("ab...");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(UntrustedText.line(null, 10)).isEmpty();
        assertThat(UntrustedText.singleLine(null)).isEmpty();
    }
}
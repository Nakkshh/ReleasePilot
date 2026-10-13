package com.releasepilot.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseAgentEvidenceTest {

    @Test
    void policyGoesLastAndIsRelabelled() {
        String text = ReleaseAgent.evidenceText(List.of(
                new AgentRunAdvisor.ToolOutput("searchReleasePolicy", "\"policy text\""),
                new AgentRunAdvisor.ToolOutput("getLatestWorkflow", "\"completed/success\""),
                new AgentRunAdvisor.ToolOutput("getIssues", "\"No open issues\"")));

        assertThat(text.indexOf("--- getLatestWorkflow ---")).isLessThan(text.indexOf("--- releasePolicy ---"));
        assertThat(text.indexOf("--- getIssues ---")).isLessThan(text.indexOf("--- releasePolicy ---"));
        assertThat(text).doesNotContain("searchReleasePolicy");
    }

    @Test
    void jsonStringLiteralsAreUnwrappedAndPlainTextIsKept() {
        assertThat(ReleaseAgent.unwrapToolText("\"line1\\nline2\"")).isEqualTo("line1\nline2");
        assertThat(ReleaseAgent.unwrapToolText("completed/success")).isEqualTo("completed/success");
        assertThat(ReleaseAgent.unwrapToolText(null)).isEmpty();
    }

    @Test
    void evidenceIsCappedWithAMarker() {
        String text = ReleaseAgent.evidenceText(List.of(
                new AgentRunAdvisor.ToolOutput("getPullRequests", "x".repeat(20_000))));

        assertThat(text.length()).isLessThan(8_100);
        assertThat(text).endsWith("(evidence truncated)\n");
    }
}
package com.releasepilot.agent;

import com.releasepilot.dto.ReleaseVerdict;

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

    @Test
    void invalidModelStatusIsNormalizedToUnknown() {
        ReleaseVerdict verdict = ReleaseAgent.normalize(
                new ReleaseVerdict("maybe", "Summary", List.of("Reason"), List.of()));

        assertThat(verdict.status()).isEqualTo("UNKNOWN");
        assertThat(verdict.reasons()).containsExactly("Reason");
    }

    @Test
    void nullModelVerdictBecomesUnknown() {
        ReleaseVerdict verdict = ReleaseAgent.normalize(null);

        assertThat(verdict.status()).isEqualTo("UNKNOWN");
        assertThat(verdict.summary()).isEqualTo("The model returned no verdict.");
        assertThat(verdict.reasons()).isEmpty();
        assertThat(verdict.blockers()).isEmpty();
    }

    @Test
    void failedStructuredCiForcesNotReadyEvenWhenModelSaysReady() {
        ReleaseVerdict model = new ReleaseVerdict("READY", "Looks good", List.of(), List.of());
        com.releasepilot.dto.github.ActionsModels.Run failedRun = run("abc123", "completed", "failure");

        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                model, failedRun, true, List.of(), true, "abc123");

        assertThat(result.status()).isEqualTo("NOT_READY");
        assertThat(result.blockers()).isNotEmpty();
    }

    @Test
    void knownFailedCiForcesNotReadyEvenWhenIssueEvidenceIsUnavailable() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), run("abc123", "completed", "failure"), true,
                null, false, "abc123");

        assertThat(result.status()).isEqualTo("NOT_READY");
    }

    @Test
    void openBugForcesNotReadyEvenWhenCiEvidenceIsUnavailable() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), null, false,
                List.of(new com.releasepilot.dto.github.GitHubModels.Issue(
                        12, "Crash on startup",
                        List.of(new com.releasepilot.dto.github.GitHubModels.Label("bug")), null)),
                true, "abc123");

        assertThat(result.status()).isEqualTo("NOT_READY");
    }

    @Test
    void openBugIssueForcesNotReadyUsingStructuredLabels() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), run("abc123", "completed", "success"), true,
                List.of(new com.releasepilot.dto.github.GitHubModels.Issue(
                        12, "Crash on startup",
                        List.of(new com.releasepilot.dto.github.GitHubModels.Label("bug")), null)),
                true, "abc123");

        assertThat(result.status()).isEqualTo("NOT_READY");
        assertThat(result.blockers()).isNotEmpty();
    }

    @Test
    void pullRequestLabelsDoNotCountAsOpenBugIssues() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), run("abc123", "completed", "success"), true,
                List.of(new com.releasepilot.dto.github.GitHubModels.Issue(
                        12, "PR with bug label",
                        List.of(new com.releasepilot.dto.github.GitHubModels.Label("bug")), new Object())),
                true, "abc123");

        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void missingCiEvidenceProducesUnknown() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), null, true, List.of(), true, "abc123");

        assertThat(result.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void ciRunForDifferentShaProducesUnknown() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), run("other-sha", "completed", "success"), true,
                List.of(), true, "abc123");

        assertThat(result.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void unavailableIssueEvidenceProducesUnknown() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                readyModel(), run("abc123", "completed", "success"), true,
                null, false, "abc123");

        assertThat(result.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void successfulCiAndNoIssuesAllowsReadyWithNoBlockers() {
        ReleaseVerdict result = ReleaseVerdictPolicy.enforce(
                new ReleaseVerdict("NOT_READY", "Model disagrees", List.of("CI passed"), List.of("Old blocker")),
                run("abc123", "completed", "success"), true, List.of(), true, "abc123");

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.blockers()).isEmpty();
    }

    private static ReleaseVerdict readyModel() {
        return new ReleaseVerdict("READY", "Looks good", List.of(), List.of());
    }

    private static com.releasepilot.dto.github.ActionsModels.Run run(
            String sha, String status, String conclusion) {
        return new com.releasepilot.dto.github.ActionsModels.Run(
                42L, "CI", "main", sha, status, conclusion, "push", 42,
                "https://example.com/run/42", "2026-10-19T10:00:00Z");
    }

}
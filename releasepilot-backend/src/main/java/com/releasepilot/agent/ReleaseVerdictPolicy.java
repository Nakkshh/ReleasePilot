package com.releasepilot.agent;

import com.releasepilot.dto.ReleaseVerdict;
import com.releasepilot.dto.github.ActionsModels;
import com.releasepilot.dto.github.GitHubModels;

import java.util.List;
import java.util.Objects;

final class ReleaseVerdictPolicy {

    private ReleaseVerdictPolicy() {
    }

    static ReleaseVerdict enforce(
            ReleaseVerdict modelVerdict,
            ActionsModels.Run ciRun,
            boolean ciEvidenceAvailable,
            List<GitHubModels.Issue> issues,
            boolean issueEvidenceAvailable,
            String targetSha) {

        boolean hasOpenBug = issueEvidenceAvailable && issues != null && issues.stream()
                .filter(Objects::nonNull)
                .filter(issue -> issue.pullRequest() == null)
                .filter(issue -> issue.labels() != null)
                .flatMap(issue -> issue.labels().stream())
                .filter(Objects::nonNull)
                .anyMatch(label -> label.name() != null
                        && "bug".equalsIgnoreCase(label.name().trim()));

        // A known blocker remains a blocker even if the other evidence source is unavailable.
        if (hasOpenBug) {
            return new ReleaseVerdict(
                    "NOT_READY",
                    "An open issue is labelled bug.",
                    List.of("An open issue has the bug label."),
                    List.of("Resolve the open bug-labelled issue before release."));
        }

        if (!ciEvidenceAvailable || ciRun == null) {
            return unknown("Required CI evidence is missing or unavailable.");
        }

        if (targetSha != null && !targetSha.isBlank()
                && (ciRun.headSha() == null || !targetSha.equalsIgnoreCase(ciRun.headSha()))) {
            return unknown("The CI run does not match the target commit.");
        }

        if ("failure".equalsIgnoreCase(ciRun.conclusion())) {
            return new ReleaseVerdict(
                    "NOT_READY",
                    "The CI workflow for the target commit failed.",
                    List.of("The CI workflow reports a failure."),
                    List.of("Fix the failed CI workflow before release."));
        }

        if (!issueEvidenceAvailable || issues == null) {
            return unknown("Required issue evidence is missing or unavailable.");
        }

        if (!"completed".equalsIgnoreCase(ciRun.status())
                || !"success".equalsIgnoreCase(ciRun.conclusion())) {
            return unknown("The CI result does not confirm success or failure.");
        }

        return new ReleaseVerdict(
                "READY",
                "The target commit's CI succeeded and no open bug-labelled issues were found.",
                List.of("The CI workflow completed successfully for the target commit.",
                        "No open bug-labelled issues were found."),
                List.of());
    }

    private static ReleaseVerdict unknown(String summary) {
        return new ReleaseVerdict("UNKNOWN", summary, List.of(), List.of());
    }
}

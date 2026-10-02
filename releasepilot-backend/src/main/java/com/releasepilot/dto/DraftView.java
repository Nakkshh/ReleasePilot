package com.releasepilot.dto;

import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;

import java.time.Instant;
import java.util.List;

/** What a human sees when deciding on a draft. Built from the stored entity. */
public record DraftView(
        Long id,
        String tag,
        boolean prerelease,
        String title,
        DraftStatus status,
        ApprovalGate approvalGate,
        String baseline,
        String targetSha,
        Instant createdAt,
        Instant expiresAt,
        ReleaseVerdict verdict,
        String checkMode,
        ReleaseNotes notes,
        int prCount,
        List<Integer> invalidReferences,
        List<Integer> omittedPrs,
        List<String> warnings,
        Instant decidedAt,
        String decidedBy,
        String decisionComment,
        boolean overrideUsed,
        String overrideReason,
        Long githubReleaseId,
        String githubReleaseUrl,
        Instant releasedAt,
        String lastError
) {
}
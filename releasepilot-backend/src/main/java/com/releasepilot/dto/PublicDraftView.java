package com.releasepilot.dto;

import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;

import java.time.Instant;
import java.util.List;

/** What anonymous visitors see: no usernames, no free-text decision/override/error fields. */
public record PublicDraftView(
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
        boolean overrideUsed,
        Long githubReleaseId,
        String githubReleaseUrl,
        Instant releasedAt,
        boolean hasError
) {
    public static PublicDraftView from(DraftView v) {
        if (v == null) {
            return null;
        }
        return new PublicDraftView(v.id(), v.tag(), v.prerelease(), v.title(), v.status(), v.approvalGate(),
                v.baseline(), v.targetSha(), v.createdAt(), v.expiresAt(), v.verdict(), v.checkMode(), v.notes(),
                v.prCount(), v.invalidReferences(), v.omittedPrs(), v.warnings(), v.decidedAt(),
                v.overrideUsed(), v.githubReleaseId(), v.githubReleaseUrl(), v.releasedAt(),
                v.lastError() != null && !v.lastError().isBlank());
    }
}
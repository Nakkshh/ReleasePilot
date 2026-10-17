package com.releasepilot.dto;

import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;

import java.time.Instant;

public record PublicDraftSummary(
        Long id,
        String tag,
        boolean prerelease,
        String title,
        DraftStatus status,
        ApprovalGate approvalGate,
        String verdictStatus,
        Instant createdAt,
        Instant expiresAt,
        Instant decidedAt
) {
    public static PublicDraftSummary from(DraftSummary s) {
        return new PublicDraftSummary(s.id(), s.tag(), s.prerelease(), s.title(), s.status(),
                s.approvalGate(), s.verdictStatus(), s.createdAt(), s.expiresAt(), s.decidedAt());
    }
}
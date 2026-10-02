package com.releasepilot.dto;

import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;

import java.time.Instant;

public record DraftSummary(
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
}
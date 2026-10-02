package com.releasepilot.service;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.exception.DraftConflictException;
import com.releasepilot.exception.DraftNotFoundException;
import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;
import com.releasepilot.model.ReleaseDraft;
import com.releasepilot.repository.ReleaseDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class DraftTransitions {

    private static final Logger log = LoggerFactory.getLogger(DraftTransitions.class);

    private final ReleaseDraftRepository drafts;
    private final DraftService service;
    private final Clock clock;

    public DraftTransitions(ReleaseDraftRepository drafts, DraftService service, Clock clock) {
        this.drafts = drafts;
        this.service = service;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReleaseDraft load(long id) {
        return drafts.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
    }

    /**
     * The atomic PENDING -> APPROVED claim. Version-checked: a concurrent claim fails the flush
     * with an optimistic-lock error (mapped to 409). noRollbackFor keeps a lazy EXPIRED transition
     * that was discovered during this call.
     */
    @Transactional(noRollbackFor = DraftConflictException.class)
    public ReleaseDraft claim(long id, ApproveRequest req) {
        ReleaseDraft d = drafts.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
        service.expireIfNeeded(d);

        ApprovalGate gate = service.gateOf(d, clock.instant());
        boolean override = false;
        String reason = null;

        switch (gate) {
            case CLOSED -> throw new DraftConflictException("DRAFT_NOT_PENDING",
                    "Draft " + id + " is " + d.getStatus() + ", not PENDING.");
            case BLOCKED_INVALID_NOTES -> throw new DraftConflictException("NOTES_INVALID",
                    "The release notes reference PRs that are not in the evidence. "
                            + "This cannot be overridden; create a new draft.");
            case NEEDS_OVERRIDE -> {
                if (!Boolean.TRUE.equals(req.override())) {
                    throw new DraftConflictException("OVERRIDE_REQUIRED",
                            "Verdict is " + d.getVerdictStatus()
                                    + ". Send override=true with an overrideReason to approve anyway.");
                }
                reason = DraftService.clean(req.overrideReason(), 1000, null);
                if (reason == null || reason.length() < 10) {
                    throw new DraftConflictException("OVERRIDE_REASON_REQUIRED",
                            "overrideReason must be at least 10 characters.");
                }
                override = true;
            }
            case OPEN -> {
                // approvable as is; any override flag is ignored and not recorded
            }
        }

        d.approve(DraftService.clean(req.decidedBy(), 100, "anonymous"),
                DraftService.clean(req.comment(), 1000, null),
                override, reason,
                DraftService.clean(req.title(), 300, null),
                clock.instant());
        drafts.saveAndFlush(d);   // surfaces version / unique-index conflicts here
        log.info("[DRAFT] id={} tag={} APPROVED by {} override={}", id, d.getTag(), d.getDecidedBy(), override);
        return d;
    }

    @Transactional
    public void markReleased(long id, long githubReleaseId, String url, Instant now) {
        ReleaseDraft d = drafts.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
        if (d.getStatus() == DraftStatus.RELEASED) {
            return;   // another request already finished this one
        }
        d.markReleased(githubReleaseId, url == null ? null : cut(url, 500), now);
    }

    @Transactional
    public void markFailed(long id, String error) {
        drafts.findById(id).ifPresent(d -> {
            if (d.getStatus() == DraftStatus.APPROVED) {   // never overwrite a RELEASED draft
                d.recordFailure(cut(error, 2000));
            }
        });
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
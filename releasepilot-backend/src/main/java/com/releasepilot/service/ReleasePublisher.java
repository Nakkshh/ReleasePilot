package com.releasepilot.service;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.ReleaseNotesResponse;
import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.exception.DraftConflictException;
import com.releasepilot.exception.GitHubApiException;
import com.releasepilot.model.DraftStatus;
import com.releasepilot.model.ReleaseDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Orchestrates approve and retry. NOT @Transactional: the GitHub call sits between two short
 * transactions (claim, then markReleased/markFailed) that live in DraftTransitions.
 */
@Service
public class ReleasePublisher {

    private static final Logger log = LoggerFactory.getLogger(ReleasePublisher.class);

    private final DraftTransitions transitions;
    private final DraftService drafts;
    private final GitHubClient github;
    private final JsonMapper json;
    private final Clock clock;

    public ReleasePublisher(DraftTransitions transitions, DraftService drafts, GitHubClient github,
                            JsonMapper json, Clock clock) {
        this.transitions = transitions;
        this.drafts = drafts;
        this.github = github;
        this.json = json;
        this.clock = clock;
    }

    public DraftView approve(long id, ApproveRequest req) {
        // Before claiming: a taken tag must leave the draft PENDING (still rejectable).
        ReleaseDraft current = transitions.load(id);
        if (current.getStatus() == DraftStatus.PENDING && github.tagExists(current.getTag())) {
            throw new DraftConflictException("TAG_EXISTS",
                    "Tag " + current.getTag() + " already exists on GitHub. Reject this draft and create a new one.");
        }

        ReleaseDraft claimed;
        try {
            claimed = transitions.claim(id, req);
        } catch (DataIntegrityViolationException e) {
            // partial unique index: another draft already holds this tag
            throw new DraftConflictException("TAG_IN_USE",
                    "Another draft has already been approved for tag " + current.getTag() + ".");
        }
        return publish(claimed, false);
    }

    public DraftView retry(long id) {
        ReleaseDraft d = transitions.load(id);
        if (d.getStatus() == DraftStatus.RELEASED) {
            return drafts.view(d);   // already done: idempotent
        }
        if (d.getStatus() != DraftStatus.APPROVED) {
            throw new DraftConflictException("DRAFT_NOT_RETRYABLE",
                    "Only APPROVED drafts can be retried; draft " + id + " is " + d.getStatus() + ".");
        }
        return publish(d, true);
    }

    private DraftView publish(ReleaseDraft d, boolean lookupFirst) {
        long id = d.getId();
        GitHubModels.Release rel = null;
        try {
            if (lookupFirst) {
                rel = github.getReleaseByTag(d.getTag());
                if (rel != null) {
                    log.warn("[RELEASE] draft {} adopting existing GitHub release {} for tag {}",
                            id, rel.id(), d.getTag());
                }
            }
            if (rel == null) {
                ReleaseNotesResponse n = json.readValue(d.getNotesJson(), ReleaseNotesResponse.class);
                rel = github.createRelease(d.getTag(), d.getTargetSha(), d.getTitle(),
                        ReleaseBodyRenderer.render(n.notes(), id), d.isPrerelease());
            }
            if (rel == null || rel.id() == null || rel.htmlUrl() == null) {
                throw new GitHubApiException("GitHub returned an unexpected response when creating the release.");
            }
        } catch (RuntimeException e) {
            recordFailure(id, e);
            if (e instanceof GitHubApiException) {
                throw new GitHubApiException("Approved, but creating the GitHub release failed: " + e.getMessage()
                        + " Draft " + id + " stays APPROVED; POST /api/release/drafts/" + id + "/retry to try again.");
            }
            throw e;
        }

        transitions.markReleased(id, rel.id(), rel.htmlUrl(), clock.instant());
        log.info("[RELEASE] draft {} RELEASED tag={} url={}", id, d.getTag(), rel.htmlUrl());
        return drafts.view(transitions.load(id));
    }

    private void recordFailure(long id, RuntimeException e) {
        log.error("[RELEASE] draft {} GitHub release failed", id, e);
        try {
            transitions.markFailed(id, e.getMessage());
        } catch (RuntimeException inner) {
            log.error("[RELEASE] could not record failure for draft {}", id, inner);   // don't mask the original
        }
    }
}
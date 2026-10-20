package com.releasepilot.service;

import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.ReleaseNotesResponse;
import com.releasepilot.dto.ReleaseCheckResponse;
import com.releasepilot.agent.ReleaseAgent;
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
import java.util.List;

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
    private final ReleaseAgent releaseAgent;
    private final JsonMapper json;
    private final Clock clock;

    public ReleasePublisher(DraftTransitions transitions, DraftService drafts, GitHubClient github,
                            ReleaseAgent releaseAgent, JsonMapper json, Clock clock) {
        this.transitions = transitions;
        this.drafts = drafts;
        this.github = github;
        this.releaseAgent = releaseAgent;
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

        // Revalidate only drafts that could otherwise be approved. Invalid notes and expired/non-pending
        // drafts are rejected by the existing transition gate without spending an LLM/API round trip.
        if (current.getStatus() == DraftStatus.PENDING
                && clock.instant().isBefore(current.getExpiresAt())
                && !current.isHasInvalidRefs()) {
            revalidateBeforeApproval(current);
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

    private void revalidateBeforeApproval(ReleaseDraft draft) {
        String targetSha = draft.getTargetSha();
        if (targetSha == null || targetSha.isBlank()) {
            throw new DraftConflictException("DRAFT_SHA_MISSING",
                    "This draft has no pinned target commit. Create a new draft before approving.");
        }

        assertHeadMatches(targetSha);
        ReleaseCheckResponse freshCheck = releaseAgent.check(targetSha);
        if (freshCheck == null || freshCheck.verdict() == null) {
            throw new DraftConflictException("READINESS_REVALIDATION_FAILED",
                    "The readiness check returned no verdict. The draft remains pending; retry approval later.");
        }
        assertHeadMatches(targetSha);
        drafts.refreshVerdict(draft.getId(), freshCheck);
        // Close the window between the potentially slow readiness check and the approval claim as much as
        // possible. GitHub cannot be transactionally locked, so publication still uses the pinned SHA.
        assertHeadMatches(targetSha);
    }

    private void assertHeadMatches(String targetSha) {
        List<GitHubModels.Commit> head = github.getRecentCommits(1);
        String currentSha = head == null || head.isEmpty() || head.get(0) == null
                ? null : head.get(0).sha();
        if (currentSha == null || !targetSha.equalsIgnoreCase(currentSha)) {
            throw new DraftConflictException("DRAFT_HEAD_CHANGED",
                    "The repository head no longer matches this draft's target commit. Create a new draft and review it again.");
        }
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
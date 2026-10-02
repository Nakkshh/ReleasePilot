package com.releasepilot.service;

import com.releasepilot.agent.ReleaseAgent;
import com.releasepilot.agent.ReleaseNotesAgent;
import com.releasepilot.dto.DraftSummary;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.ReleaseCheckResponse;
import com.releasepilot.dto.ReleaseNotesResponse;
import com.releasepilot.dto.ReleaseVerdict;
import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.exception.DraftConflictException;
import com.releasepilot.exception.DraftNotFoundException;
import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;
import com.releasepilot.model.ReleaseDraft;
import com.releasepilot.repository.ReleaseDraftRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);
    private static final Set<String> VERDICTS = Set.of("READY", "NOT_READY", "UNKNOWN");

    private final ReleaseDraftRepository drafts;
    private final ReleaseAgent releaseAgent;
    private final ReleaseNotesAgent notesAgent;
    private final GitHubClient github;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration ttl;

    public DraftService(ReleaseDraftRepository drafts,
                        ReleaseAgent releaseAgent,
                        ReleaseNotesAgent notesAgent,
                        GitHubClient github,
                        JsonMapper json,
                        Clock clock,
                        @Value("${release.draft-ttl-hours:24}") long ttlHours) {
        this.drafts = drafts;
        this.releaseAgent = releaseAgent;
        this.notesAgent = notesAgent;
        this.github = github;
        this.json = json;
        this.clock = clock;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Deliberately NOT @Transactional: the agents make slow LLM calls and no DB transaction
     * should be held open across them. Only the final save() runs in its own transaction.
     */
    public DraftView create(String tag, boolean prerelease) {
        // 1. Cheap guards first, before any LLM call.
        if (drafts.existsByTagAndStatusIn(tag, List.of(DraftStatus.APPROVED, DraftStatus.RELEASED))) {
            throw new DraftConflictException("TAG_IN_USE",
                    "A draft for tag " + tag + " is already approved or released.");
        }
        if (github.tagExists(tag)) {
            throw new DraftConflictException("TAG_EXISTS",
                    "Tag " + tag + " already exists on GitHub. Choose a new version.");
        }
        List<GitHubModels.Commit> head = github.getRecentCommits(1);
        String targetSha = head == null || head.isEmpty() ? null : head.get(0).sha();
        if (targetSha == null) {
            throw new DraftConflictException("NO_COMMITS", "The repository has no commits to release.");
        }

        // 2. Notes first (one cheap call). Nothing to release means no point running the slow check.
        ReleaseNotesResponse notes = notesAgent.draft();
        if (notes.prCount() == 0) {
            throw new DraftConflictException("NO_CHANGES",
                    "No merged pull requests " + notes.baseline() + ". Nothing to release.");
        }

        // 3. Release readiness check.
        ReleaseCheckResponse check = releaseAgent.check();

        // 4. Persist.
        Instant now = clock.instant();
        String title = notes.notes() == null || notes.notes().title() == null || notes.notes().title().isBlank()
                ? tag : notes.notes().title().trim();
        boolean invalidRefs = notes.invalidReferences() != null && !notes.invalidReferences().isEmpty();

        ReleaseDraft draft = new ReleaseDraft(
                tag, prerelease, cut(title, 300),
                json.writeValueAsString(notes),
                json.writeValueAsString(check),
                normalizeVerdict(check.verdict()),
                invalidRefs,
                cut(notes.baseline(), 300),
                targetSha,
                now, now.plus(ttl));
        draft = drafts.save(draft);

        log.info("[DRAFT] created id={} tag={} verdict={} invalidRefs={} prs={} target={}",
                draft.getId(), tag, draft.getVerdictStatus(), invalidRefs, notes.prCount(), targetSha);
        return view(draft);
    }

    /** Read one draft. May persist a lazy EXPIRED transition. */
    @Transactional
    public DraftView get(long id) {
        ReleaseDraft d = find(id);
        expireIfNeeded(d);
        return view(d);
    }

    /** Newest 50 drafts. May persist lazy EXPIRED transitions. */
    @Transactional
    public List<DraftSummary> list() {
        return drafts.findTop50ByOrderByCreatedAtDesc().stream()
                .map(d -> {
                    expireIfNeeded(d);
                    return summary(d);
                })
                .toList();
    }

    /**
     * noRollbackFor: if the draft turned out to be expired, the EXPIRED transition must be saved
     * even though this request itself ends in a 409.
     */
    @Transactional(noRollbackFor = DraftConflictException.class)
    public DraftView reject(long id, String decidedBy, String comment) {
        ReleaseDraft d = find(id);
        expireIfNeeded(d);
        if (d.getStatus() != DraftStatus.PENDING) {
            throw new DraftConflictException("DRAFT_NOT_PENDING",
                    "Draft " + id + " is " + d.getStatus() + ", not PENDING.");
        }
        d.reject(clean(decidedBy, 100, "anonymous"), clean(comment, 1000, null), clock.instant());
        drafts.save(d);
        log.info("[DRAFT] id={} tag={} REJECTED by {}", id, d.getTag(), d.getDecidedBy());
        return view(d);
    }

    /** Whether a human may approve this draft right now. Single source of truth (reused by approve in 8.4). */
    public ApprovalGate gateOf(ReleaseDraft d, Instant now) {
        if (d.getStatus() != DraftStatus.PENDING || !now.isBefore(d.getExpiresAt())) {
            return ApprovalGate.CLOSED;
        }
        if (d.isHasInvalidRefs()) {
            return ApprovalGate.BLOCKED_INVALID_NOTES;
        }
        if (!"READY".equals(d.getVerdictStatus())) {
            return ApprovalGate.NEEDS_OVERRIDE;
        }
        return ApprovalGate.OPEN;
    }

    public DraftView view(ReleaseDraft d) {
        ReleaseNotesResponse n = json.readValue(d.getNotesJson(), ReleaseNotesResponse.class);
        ReleaseCheckResponse c = json.readValue(d.getVerdictJson(), ReleaseCheckResponse.class);
        ApprovalGate gate = gateOf(d, clock.instant());

        List<String> warnings = new ArrayList<>();
        if (!n.invalidReferences().isEmpty()) {
            warnings.add("The notes mention PR numbers that are not in the evidence: " + n.invalidReferences()
                    + ". Approval is blocked; create a new draft.");
        }
        if (!n.omittedPrs().isEmpty()) {
            warnings.add("The notes leave out these merged PRs: " + n.omittedPrs() + ".");
        }
        if (!"READY".equals(d.getVerdictStatus())) {
            warnings.add("Release check verdict is " + d.getVerdictStatus()
                    + ". Approval requires override=true with a reason.");
        }

        return new DraftView(
                d.getId(), d.getTag(), d.isPrerelease(), d.getTitle(), d.getStatus(), gate,
                d.getBaseline(), d.getTargetSha(), d.getCreatedAt(), d.getExpiresAt(),
                c.verdict(), c.mode(), n.notes(), n.prCount(),
                n.invalidReferences(), n.omittedPrs(), warnings,
                d.getDecidedAt(), d.getDecidedBy(), d.getDecisionComment(),
                d.isOverrideUsed(), d.getOverrideReason(),
                d.getGithubReleaseId(), d.getGithubReleaseUrl(), d.getReleasedAt(), d.getLastError());
    }

    private DraftSummary summary(ReleaseDraft d) {
        return new DraftSummary(d.getId(), d.getTag(), d.isPrerelease(), d.getTitle(), d.getStatus(),
                gateOf(d, clock.instant()), d.getVerdictStatus(),
                d.getCreatedAt(), d.getExpiresAt(), d.getDecidedAt());
    }

    private ReleaseDraft find(long id) {
        return drafts.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
    }

    void expireIfNeeded(ReleaseDraft d) {
        if (d.getStatus() == DraftStatus.PENDING && !clock.instant().isBefore(d.getExpiresAt())) {
            d.expire();   // managed entity: flushed at commit
            log.info("[DRAFT] id={} tag={} EXPIRED", d.getId(), d.getTag());
        }
    }

    static String normalizeVerdict(ReleaseVerdict v) {
        if (v == null || v.status() == null) {
            return "UNKNOWN";
        }
        String s = v.status().trim().toUpperCase(Locale.ROOT);
        return VERDICTS.contains(s) ? s : "UNKNOWN";
    }

    static String clean(String s, int max, String fallback) {
        if (s == null) {
            return fallback;
        }
        String t = s.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return fallback;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
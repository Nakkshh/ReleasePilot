package com.releasepilot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "release_drafts")
public class ReleaseDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tag;

    @Column(nullable = false)
    private boolean prerelease;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "notes_json", nullable = false, columnDefinition = "text")
    private String notesJson;

    @Column(name = "verdict_json", nullable = false, columnDefinition = "text")
    private String verdictJson;

    @Column(name = "verdict_status", nullable = false, length = 16)
    private String verdictStatus;

    @Column(name = "has_invalid_refs", nullable = false)
    private boolean hasInvalidRefs;

    @Column(length = 300)
    private String baseline;

    @Column(name = "target_sha", length = 64)
    private String targetSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DraftStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "override_used", nullable = false)
    private boolean overrideUsed;

    @Column(name = "override_reason", length = 1000)
    private String overrideReason;

    @Column(name = "github_release_id")
    private Long githubReleaseId;

    @Column(name = "github_release_url", length = 500)
    private String githubReleaseUrl;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Version
    private long version;

    protected ReleaseDraft() {
        // for JPA
    }

    public ReleaseDraft(String tag, boolean prerelease, String title,
                        String notesJson, String verdictJson, String verdictStatus,
                        boolean hasInvalidRefs, String baseline, String targetSha,
                        Instant createdAt, Instant expiresAt) {
        this.tag = tag;
        this.prerelease = prerelease;
        this.title = title;
        this.notesJson = notesJson;
        this.verdictJson = verdictJson;
        this.verdictStatus = verdictStatus;
        this.hasInvalidRefs = hasInvalidRefs;
        this.baseline = baseline;
        this.targetSha = targetSha;
        this.status = DraftStatus.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** PENDING -> APPROVED. Caller has validated the gate. */
    public void approve(String by, String comment, boolean overrideUsed, String overrideReason,
                        String newTitle, Instant now) {
        this.status = DraftStatus.APPROVED;
        this.decidedAt = now;
        this.decidedBy = by;
        this.decisionComment = comment;
        this.overrideUsed = overrideUsed;
        this.overrideReason = overrideReason;
        if (newTitle != null) {
            this.title = newTitle;
        }
        this.lastError = null;
    }

    /** APPROVED -> RELEASED. */
    public void markReleased(long githubReleaseId, String url, Instant now) {
        this.status = DraftStatus.RELEASED;
        this.githubReleaseId = githubReleaseId;
        this.githubReleaseUrl = url;
        this.releasedAt = now;
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.lastError = error;
    }

    /** PENDING -> REJECTED. Caller must have checked the status. */
    public void reject(String by, String comment, Instant now) {
        this.status = DraftStatus.REJECTED;
        this.decidedAt = now;
        this.decidedBy = by;
        this.decisionComment = comment;
    }

    /** PENDING -> EXPIRED. Recorded at the moment it actually expired. */
    public void expire() {
        this.status = DraftStatus.EXPIRED;
        this.decidedAt = this.expiresAt;
        this.decidedBy = "system";
        this.decisionComment = "Expired without a decision";
    }

    public Long getId() { return id; }
    public String getTag() { return tag; }
    public boolean isPrerelease() { return prerelease; }
    public String getTitle() { return title; }
    public String getNotesJson() { return notesJson; }
    public String getVerdictJson() { return verdictJson; }
    public String getVerdictStatus() { return verdictStatus; }
    public boolean isHasInvalidRefs() { return hasInvalidRefs; }
    public String getBaseline() { return baseline; }
    public String getTargetSha() { return targetSha; }
    public DraftStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionComment() { return decisionComment; }
    public boolean isOverrideUsed() { return overrideUsed; }
    public String getOverrideReason() { return overrideReason; }
    public Long getGithubReleaseId() { return githubReleaseId; }
    public String getGithubReleaseUrl() { return githubReleaseUrl; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getLastError() { return lastError; }
    public long getVersion() { return version; }

}
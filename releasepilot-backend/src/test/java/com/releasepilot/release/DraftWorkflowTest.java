package com.releasepilot.release;

import com.releasepilot.agent.ReleaseAgent;
import com.releasepilot.agent.ReleaseNotesAgent;
import com.releasepilot.dto.ApproveRequest;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.ReleaseCheckResponse;
import com.releasepilot.dto.ReleaseNotes;
import com.releasepilot.dto.ReleaseNotesResponse;
import com.releasepilot.dto.ReleaseVerdict;
import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.exception.DraftConflictException;
import com.releasepilot.exception.GitHubApiException;
import com.releasepilot.model.ApprovalGate;
import com.releasepilot.model.DraftStatus;
import com.releasepilot.model.ReleaseDraft;
import com.releasepilot.repository.ReleaseDraftRepository;
import com.releasepilot.service.DraftService;
import com.releasepilot.service.GitHubClient;
import com.releasepilot.service.ReleasePublisher;
import com.releasepilot.support.MutableClock;
import com.releasepilot.support.TestClockConfig;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class DraftWorkflowTest {

    private static final String SHA = "2cec45a4a7981a41fa0bd5dd0be6939c2dcef741";

    @Autowired DraftService drafts;
    @Autowired ReleasePublisher publisher;
    @Autowired ReleaseDraftRepository repo;
    @Autowired MutableClock clock;
    @Autowired JsonMapper json;
    @Value("${spring.datasource.url}") String dbUrl;

    @MockitoBean GitHubClient github;
    @MockitoBean ReleaseAgent releaseAgent;
    @MockitoBean ReleaseNotesAgent notesAgent;

    @BeforeEach
    void setUp() {
        assertThat(dbUrl).as("tests delete all drafts; they must never run against the real database")
                .endsWith("_test");
        repo.deleteAll();
        clock.set(java.time.Instant.parse("2026-10-02T10:00:00Z"));
    }

    // ---------------------------------------------------------------- draft creation

    @Test
    void create_savesPendingDraft_withPinnedSha() {
        stubAgents(2, List.of(), "READY");

        DraftView v = drafts.create("v1.0.0", false);

        assertThat(v.status()).isEqualTo(DraftStatus.PENDING);
        assertThat(v.approvalGate()).isEqualTo(ApprovalGate.OPEN);
        assertThat(v.targetSha()).isEqualTo(SHA);
        assertThat(v.prCount()).isEqualTo(2);
        assertThat(repo.count()).isEqualTo(1);
    }

    @Test
    void create_noChanges_refusesBeforeRunningTheSlowCheck() {
        when(github.getRecentCommits(1)).thenReturn(List.of(new GitHubModels.Commit(SHA, null)));
        when(notesAgent.draft()).thenReturn(notesResponse(0, List.of()));

        assertConflict(() -> drafts.create("v1.0.0", false), "NO_CHANGES");

        verifyNoInteractions(releaseAgent);
        assertThat(repo.count()).isZero();
    }

    @Test
    void create_tagAlreadyOnGithub_isRefusedWithoutAnyAgentCall() {
        when(github.tagExists("v1.0.0")).thenReturn(true);

        assertConflict(() -> drafts.create("v1.0.0", false), "TAG_EXISTS");

        verifyNoInteractions(notesAgent, releaseAgent);
        assertThat(repo.count()).isZero();
    }

    @Test
    void create_tagHeldByReleasedDraft_isRefused() {
        releasedDraft("v1.0.0");

        assertConflict(() -> drafts.create("v1.0.0", false), "TAG_IN_USE");

        verifyNoInteractions(notesAgent, releaseAgent);
    }

    @Test
    void create_withInvalidReferences_blocksApproval() {
        stubAgents(2, List.of(99), "READY");

        DraftView v = drafts.create("v1.0.0", false);

        assertThat(v.approvalGate()).isEqualTo(ApprovalGate.BLOCKED_INVALID_NOTES);
        assertThat(v.invalidReferences()).containsExactly(99);
        assertConflict(() -> publisher.approve(v.id(), approve()), "NOTES_INVALID");
    }

    // ---------------------------------------------------------------- approve

    @Test
    void approve_ready_createsExactlyOneRelease_withRenderedBody() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        stubCreateOk("v1.0.0", 123L);

        DraftView v = publisher.approve(d.getId(), approve());

        assertThat(v.status()).isEqualTo(DraftStatus.RELEASED);
        assertThat(v.githubReleaseId()).isEqualTo(123L);
        assertThat(v.githubReleaseUrl()).contains("v1.0.0");
        assertThat(v.overrideUsed()).isFalse();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(github, times(1)).createRelease(eq("v1.0.0"), eq(SHA), any(), body.capture(), eq(false));
        assertThat(body.getValue()).contains("## Features", "#2 feat: add thing (@dev)",
                "## Fixes", "draft #" + d.getId());
    }

    @Test
    void approve_twice_doesNotCreateASecondRelease() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        stubCreateOk("v1.0.0", 123L);
        publisher.approve(d.getId(), approve());

        assertConflict(() -> publisher.approve(d.getId(), approve()), "DRAFT_NOT_PENDING");

        verify(github, times(1)).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void approve_afterReject_isRefused() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        drafts.reject(d.getId(), "tester", "not now");

        assertConflict(() -> publisher.approve(d.getId(), approve()), "DRAFT_NOT_PENDING");

        verify(github, never()).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void approve_notReady_followsTheOverrideRules() {
        ReleaseDraft d = saveDraft("v1.0.0", "NOT_READY", false);
        stubCreateOk("v1.0.0", 5L);

        assertConflict(() -> publisher.approve(d.getId(), approve()), "OVERRIDE_REQUIRED");
        assertConflict(() -> publisher.approve(d.getId(),
                new ApproveRequest("tester", null, true, "short", null)), "OVERRIDE_REASON_REQUIRED");
        verify(github, never()).createRelease(any(), any(), any(), any(), anyBoolean());
        assertThat(repo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DraftStatus.PENDING);

        DraftView v = publisher.approve(d.getId(), new ApproveRequest("tester", null, true,
                "Known flaky check, accepted by the release manager", null));

        assertThat(v.status()).isEqualTo(DraftStatus.RELEASED);
        assertThat(v.overrideUsed()).isTrue();
        assertThat(v.overrideReason()).contains("accepted by the release manager");
    }

    @Test
    void approve_ready_ignoresAnOverrideFlag() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        stubCreateOk("v1.0.0", 5L);

        DraftView v = publisher.approve(d.getId(),
                new ApproveRequest("tester", null, true, "not actually needed here", null));

        assertThat(v.overrideUsed()).isFalse();
        assertThat(v.overrideReason()).isNull();
    }

    @Test
    void approve_invalidNotes_cannotBeOverridden() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", true);

        assertConflict(() -> publisher.approve(d.getId(),
                new ApproveRequest("tester", null, true, "forcing it through anyway", null)), "NOTES_INVALID");

        verify(github, never()).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void approve_tagTakenOnGithub_leavesTheDraftPending() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        when(github.tagExists("v1.0.0")).thenReturn(true);

        assertConflict(() -> publisher.approve(d.getId(), approve()), "TAG_EXISTS");

        assertThat(repo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DraftStatus.PENDING);
        verify(github, never()).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void approve_titleOverride_isUsedAsTheReleaseName() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        stubCreateOk("v1.0.0", 5L);

        DraftView v = publisher.approve(d.getId(),
                new ApproveRequest("tester", null, null, null, "Corrected title"));

        assertThat(v.title()).isEqualTo("Corrected title");
        verify(github).createRelease(eq("v1.0.0"), eq(SHA), eq("Corrected title"), any(), eq(false));
    }

    // ---------------------------------------------------------------- failure and retry

    @Test
    void approve_githubFailure_keepsApprovedAndRecordsTheError() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        when(github.createRelease(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new GitHubApiException("boom 422"));

        assertThatThrownBy(() -> publisher.approve(d.getId(), approve()))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("boom 422")
                .hasMessageContaining("retry");

        ReleaseDraft after = repo.findById(d.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(DraftStatus.APPROVED);
        assertThat(after.getLastError()).contains("boom 422");
        assertThat(after.getGithubReleaseId()).isNull();
    }

    @Test
    void retry_afterFailure_createsTheRelease() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        when(github.createRelease(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new GitHubApiException("boom"))
                .thenReturn(release("v1.0.0", 321L));
        assertThatThrownBy(() -> publisher.approve(d.getId(), approve())).isInstanceOf(GitHubApiException.class);

        DraftView v = publisher.retry(d.getId());

        assertThat(v.status()).isEqualTo(DraftStatus.RELEASED);
        assertThat(v.githubReleaseId()).isEqualTo(321L);
        assertThat(v.lastError()).isNull();
        verify(github, times(2)).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void retry_adoptsAnExistingRelease_insteadOfCreatingAnother() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        when(github.createRelease(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new GitHubApiException("response lost"));
        assertThatThrownBy(() -> publisher.approve(d.getId(), approve())).isInstanceOf(GitHubApiException.class);
        when(github.getReleaseByTag("v1.0.0")).thenReturn(release("v1.0.0", 777L));

        DraftView v = publisher.retry(d.getId());

        assertThat(v.status()).isEqualTo(DraftStatus.RELEASED);
        assertThat(v.githubReleaseId()).isEqualTo(777L);
        verify(github, times(1)).createRelease(any(), any(), any(), any(), anyBoolean());   // only the failed one
    }

    @Test
    void retry_onAReleasedDraft_isIdempotent() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        stubCreateOk("v1.0.0", 123L);
        publisher.approve(d.getId(), approve());

        DraftView v = publisher.retry(d.getId());

        assertThat(v.status()).isEqualTo(DraftStatus.RELEASED);
        verify(github, times(1)).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void retry_onAPendingDraft_isRefused() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);

        assertConflict(() -> publisher.retry(d.getId()), "DRAFT_NOT_RETRYABLE");
    }

    // ---------------------------------------------------------------- expiry and reject

    @Test
    void approve_expiredDraft_isRefused_andTheExpiryIsPersisted() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        clock.advance(Duration.ofHours(25));

        assertConflict(() -> publisher.approve(d.getId(), approve()), "DRAFT_NOT_PENDING");

        ReleaseDraft after = repo.findById(d.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(DraftStatus.EXPIRED);   // proves noRollbackFor works
        verify(github, never()).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void get_expiresAPendingDraftLazily() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        clock.advance(Duration.ofHours(25));

        DraftView v = drafts.get(d.getId());

        assertThat(v.status()).isEqualTo(DraftStatus.EXPIRED);
        assertThat(v.approvalGate()).isEqualTo(ApprovalGate.CLOSED);
        assertThat(v.decidedBy()).isEqualTo("system");
    }

    @Test
    void reject_twice_secondIsRefused() {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        drafts.reject(d.getId(), "tester", "no");

        assertConflict(() -> drafts.reject(d.getId(), "tester", "again"), "DRAFT_NOT_PENDING");
    }

    // ---------------------------------------------------------------- database backstops

    @Test
    void uniqueIndex_stopsASecondApprovalForTheSameTag() {
        ReleaseDraft first = saveDraft("v1.0.0", "READY", false);
        ReleaseDraft second = saveDraft("v1.0.0", "READY", false);   // two PENDING drafts per tag are allowed
        stubCreateOk("v1.0.0", 1L);
        publisher.approve(first.getId(), approve());

        // GitHub's tag check says "free" (simulating lag); the database index must still refuse.
        assertConflict(() -> publisher.approve(second.getId(), approve()), "TAG_IN_USE");

        verify(github, times(1)).createRelease(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void concurrentApprovals_createExactlyOneRelease() throws Exception {
        ReleaseDraft d = saveDraft("v1.0.0", "READY", false);
        when(github.createRelease(any(), any(), any(), any(), anyBoolean())).thenAnswer(inv -> {
            Thread.sleep(300);   // widen the race window
            return release("v1.0.0", 42L);
        });

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        publisher.approve(d.getId(), approve());
                        return "OK";
                    } catch (DraftConflictException e) {
                        return e.getCode();
                    } catch (OptimisticLockingFailureException e) {
                        return "LOCK";
                    }
                }));
            }
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(results.stream().filter("OK"::equals).count()).as("results: %s", results).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        verify(github, times(1)).createRelease(any(), any(), any(), any(), anyBoolean());
        assertThat(repo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(DraftStatus.RELEASED);
    }

    // ---------------------------------------------------------------- helpers

    private static void assertConflict(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DraftConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static ApproveRequest approve() {
        return new ApproveRequest("tester", "ok", null, null, null);
    }

    private static GitHubModels.Release release(String tag, long id) {
        return new GitHubModels.Release(tag, tag, false, false, "2026-10-02T10:00:00Z",
                "https://github.com/test/test/releases/tag/" + tag, id);
    }

    private void stubCreateOk(String tag, long id) {
        when(github.createRelease(any(), any(), any(), any(), anyBoolean())).thenReturn(release(tag, id));
    }

    private void stubAgents(int prCount, List<Integer> invalid, String verdict) {
        when(github.getRecentCommits(1)).thenReturn(List.of(new GitHubModels.Commit(SHA, null)));
        when(notesAgent.draft()).thenReturn(notesResponse(prCount, invalid));
        when(releaseAgent.check()).thenReturn(checkResponse(verdict));
    }

    private ReleaseNotesResponse notesResponse(int prCount, List<Integer> invalid) {
        ReleaseNotes n = new ReleaseNotes("Release notes", "Two changes.",
                List.of("#2 feat: add thing (@dev)"), List.of("#4 fix: repair thing (@dev)"),
                List.of(), List.of());
        return new ReleaseNotesResponse(n, "last 20 merged pull requests (no previous release)", prCount,
                invalid, List.of(), 1, 10L, 100, 50, clock.instant());
    }

    private ReleaseCheckResponse checkResponse(String status) {
        return new ReleaseCheckResponse(new ReleaseVerdict(status, "summary", List.of("reason"), List.of()),
                "fallback", "test", List.of(), 1, 0, 10L, 100, 50, clock.instant());
    }

    private ReleaseDraft saveDraft(String tag, String verdict, boolean invalidRefs) {
        ReleaseNotesResponse nr = notesResponse(2, invalidRefs ? List.of(99) : List.of());
        ReleaseCheckResponse cr = checkResponse(verdict);
        return repo.save(new ReleaseDraft(tag, false, "Release notes",
                json.writeValueAsString(nr), json.writeValueAsString(cr), verdict, invalidRefs,
                nr.baseline(), SHA, clock.instant(), clock.instant().plus(Duration.ofHours(24))));
    }

    private ReleaseDraft releasedDraft(String tag) {
        ReleaseDraft d = saveDraft(tag, "READY", false);
        d.approve("tester", null, false, null, null, clock.instant());
        d.markReleased(1L, "https://example.test/release", clock.instant());
        return repo.save(d);
    }
}
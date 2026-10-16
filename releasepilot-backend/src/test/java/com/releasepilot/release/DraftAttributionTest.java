package com.releasepilot.release;

import com.releasepilot.agent.ReleaseAgent;
import com.releasepilot.agent.ReleaseNotesAgent;
import com.releasepilot.dto.DraftView;
import com.releasepilot.dto.ReleaseCheckResponse;
import com.releasepilot.dto.ReleaseNotes;
import com.releasepilot.dto.ReleaseNotesResponse;
import com.releasepilot.dto.ReleaseVerdict;
import com.releasepilot.dto.github.GitHubModels;
import com.releasepilot.repository.ReleaseDraftRepository;
import com.releasepilot.service.DraftService;
import com.releasepilot.service.GitHubClient;
import com.releasepilot.support.MutableClock;
import com.releasepilot.support.TestClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class DraftAttributionTest {

    private static final String SHA = "2cec45a4a7981a41fa0bd5dd0be6939c2dcef741";

    @Autowired DraftService drafts;
    @Autowired ReleaseDraftRepository repo;
    @Autowired MutableClock clock;
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

        ReleaseNotes n = new ReleaseNotes("Release notes", "Two changes.",
                List.of("#2 feat: add thing (@dev)"), List.of("#4 fix: repair thing (@dev)"),
                List.of(), List.of());
        when(github.getRecentCommits(1)).thenReturn(List.of(new GitHubModels.Commit(SHA, null)));
        when(notesAgent.draft()).thenReturn(new ReleaseNotesResponse(n,
                "last 20 merged pull requests (no previous release)", 2,
                List.of(), List.of(), 1, 10L, 100, 50, clock.instant()));
        when(releaseAgent.check()).thenReturn(new ReleaseCheckResponse(
                new ReleaseVerdict("READY", "summary", List.of("reason"), List.of()),
                "fallback", "test", List.of(), 1, 0, 10L, 100, 50, clock.instant()));
    }

    @Test
    void theCreatorIsStoredAndShown() {
        DraftView v = drafts.create("v2.0.0", false, "  alice ");

        assertThat(v.createdBy()).isEqualTo("alice");
        assertThat(repo.findById(v.id()).orElseThrow().getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void creatingWithoutAUserStillWorks() {
        DraftView v = drafts.create("v2.0.1", false);

        assertThat(v.createdBy()).isNull();
        assertThat(repo.findById(v.id()).orElseThrow().getCreatedBy()).isNull();
    }
}
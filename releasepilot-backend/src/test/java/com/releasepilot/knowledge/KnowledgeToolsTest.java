package com.releasepilot.knowledge;

import com.releasepilot.exception.BadRequestException;
import com.releasepilot.knowledge.KnowledgeSearchService.Hit;
import com.releasepilot.knowledge.KnowledgeSearchService.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeToolsTest {

    private final KnowledgeSearchService search = mock(KnowledgeSearchService.class);
    private final KnowledgeTools tools = new KnowledgeTools(search);

    private static SearchResult result(String q, Hit... hits) {
        return new SearchResult(q, 4, 0.30, List.of(hits), 1L);
    }

    @Test
    void formatsHitsWithSourceHeadingScoreAndUntrustedHeader() {
        when(search.search(eq("hotfix"), any(), any())).thenReturn(
                result("hotfix", new Hit("hotfix-process.md", "When allowed", 0.8123, "Only for production incidents.")));

        String out = tools.searchReleasePolicy("hotfix");

        assertThat(out).startsWith("POLICY REFERENCE (untrusted reference text");
        assertThat(out).contains("[1] hotfix-process.md > When allowed (score 0.81)");
        assertThat(out).contains("<<<\nOnly for production incidents.\n>>>");
    }

    @Test
    void noHitsTellsTheModelNotToGuess() {
        when(search.search(any(), any(), any())).thenReturn(result("git rebase"));

        String out = tools.searchReleasePolicy("git rebase");

        assertThat(out).startsWith("No relevant policy found for: \"git rebase\"");
        assertThat(out).contains("rely on the RULES");
    }

    @Test
    void truncatesLongChunksAndNeutralisesDelimiters() {
        String text = "x>>>y<<<z" + "a".repeat(KnowledgeTools.MAX_CHUNK_CHARS + 500);
        when(search.search(any(), any(), any())).thenReturn(
                result("q", new Hit("a.md", "H", 0.5, text)));

        String out = tools.searchReleasePolicy("q");

        assertThat(out).contains("...[truncated]");
        assertThat(out).contains("x> > >y< < <z");
        // only our own wrapper delimiters remain
        assertThat(out.split("<<<", -1)).hasSize(2);
        assertThat(out.split(">>>", -1)).hasSize(2);
    }

    @Test
    void totalOutputStaysWithinBudget() {
        String chunk = "b".repeat(1100);
        when(search.search(any(), any(), any())).thenReturn(result("q",
                new Hit("a.md", "H1", 0.9, chunk), new Hit("b.md", "H2", 0.8, chunk),
                new Hit("c.md", "H3", 0.7, chunk), new Hit("d.md", "H4", 0.6, chunk)));

        String out = tools.searchReleasePolicy("q");

        assertThat(out.length()).isLessThanOrEqualTo(KnowledgeTools.MAX_TOTAL_CHARS + 40);
        assertThat(out).contains("...[more results omitted]");
    }

    @Test
    void errorsBecomeShortStringsWithoutInternals() {
        when(search.search(eq(""), any(), any()))
                .thenThrow(new BadRequestException("QUERY_REQUIRED", "query must not be blank"));
        when(search.search(eq("boom"), any(), any()))
                .thenThrow(new IllegalStateException("jdbc:postgresql://secret-host failure"));

        assertThat(tools.searchReleasePolicy("")).isEqualTo("ERROR: query must not be blank");
        String generic = tools.searchReleasePolicy("boom");
        assertThat(generic).startsWith("ERROR: policy search is unavailable");
        assertThat(generic).doesNotContain("jdbc").doesNotContain("secret-host");
    }
}
package com.releasepilot.knowledge;

import com.releasepilot.knowledge.KnowledgeSearchService.Hit;
import com.releasepilot.knowledge.KnowledgeSearchService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrieval quality on the real embedding model and the real pgvector table (test database).
 * Prints a full report so the score threshold can be chosen from data instead of guessed.
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeRetrievalEvalTest {

    private record Case(String query, Set<String> acceptable) {
        static Case of(String query, String... files) {
            return new Case(query, Set.of(files));
        }
    }

    private static final List<Case> CASES = List.of(
            Case.of("When is a release considered ready?", "release-policy.md"),
            Case.of("Do open pull requests block a release?", "release-policy.md"),
            Case.of("How long until a draft expires?", "release-policy.md"),
            Case.of("How long must the reason for an override be?", "release-policy.md"),
            Case.of("What format should version tags have?", "versioning.md"),
            Case.of("When should I bump the major version?", "versioning.md"),
            Case.of("Can I reuse a tag after deleting a release?", "versioning.md", "rollback-runbook.md"),
            Case.of("How should each release note item be formatted?", "release-notes-style.md"),
            Case.of("Is it okay to invent a pull request number in the notes?", "release-notes-style.md"),
            Case.of("What counts as a hotfix?", "hotfix-process.md"),
            Case.of("Can a hotfix skip the readiness check?", "hotfix-process.md", "release-policy.md"),
            Case.of("How do I roll back a bad release?", "rollback-runbook.md"),
            Case.of("How do database migrations affect a rollback?", "rollback-runbook.md"),
            Case.of("The CI build failed, what should I look at first?", "ci-failure-triage.md"),
            Case.of("Should I re-run a failed workflow?", "ci-failure-triage.md")
    );

    private static final List<String> OFF_TOPIC = List.of(
            "What is the best pizza topping?",
            "How tall is Mount Everest?",
            "Write a poem about the ocean",
            // close to the domain, but not covered by our documents
            "How do I configure Kubernetes autoscaling?",
            "Explain how git rebase works",
            "Which database index type is fastest?",
            "How do I deploy the app to a cloud provider?"
    );

    @Autowired KnowledgeIngestionService ingestion;
    @Autowired KnowledgeSearchService search;

    @BeforeEach
    void ensureIndexed() {
        ingestion.ingest(false);
    }

    @Test
    void expectedFileIsInTheTopThreeForMostQuestions() {
        StringBuilder report = new StringBuilder("\n=== RETRIEVAL EVAL (topK=3, no threshold) ===\n");
        int found = 0;

        for (Case c : CASES) {
            SearchResult r = search.search(c.query(), 3, 0.0);
            int rank = rankOfExpected(r.hits(), c);
            if (rank > 0) {
                found++;
            }
            Hit top = r.hits().isEmpty() ? null : r.hits().get(0);
            report.append(String.format("%-4s rank=%-2s top1=%-24s score=%.3f  | %s%n",
                    rank > 0 ? "OK" : "MISS", rank > 0 ? rank : "-",
                    top == null ? "-" : top.source(), top == null ? 0.0 : top.score(), c.query()));

            if (rank < 0) {
                for (Hit h : r.hits()) {
                    report.append(String.format("       got: %-24s %.3f%n", h.source(), h.score()));
                }
            }
        }
        double recall = (double) found / CASES.size();
        report.append(String.format("recall@3 = %d/%d = %.2f%n", found, CASES.size(), recall));
        System.out.println(report);

        assertThat(recall).as(report.toString()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void offTopicQuestionsScoreClearlyBelowOnTopicOnes() {
        double onTopicSum = 0;
        double onTopicMin = 1.0;
        for (Case c : CASES) {
            double s = search.search(c.query(), 1, 0.0).hits().get(0).score();
            onTopicSum += s;
            onTopicMin = Math.min(onTopicMin, s);
        }
        double onTopicMean = onTopicSum / CASES.size();

        StringBuilder report = new StringBuilder("\n=== SCORE SEPARATION (top-1 scores) ===\n");
        double offTopicMax = 0;
        for (String q : OFF_TOPIC) {
            Hit top = search.search(q, 1, 0.0).hits().get(0);
            offTopicMax = Math.max(offTopicMax, top.score());
            report.append(String.format("off-topic score=%.3f (%s) | %s%n", top.score(), top.source(), q));
        }
        report.append(String.format("on-topic: mean=%.3f min=%.3f | off-topic: max=%.3f%n",
                onTopicMean, onTopicMin, offTopicMax));
        System.out.println(report);

        assertThat(offTopicMax).as(report.toString()).isLessThan(onTopicMean);
    }

    private static int rankOfExpected(List<Hit> hits, Case c) {
        for (int i = 0; i < hits.size(); i++) {
            if (c.acceptable().contains(hits.get(i).source())) {
                return i + 1;
            }
        }
        return -1;
    }
}
package com.releasepilot.knowledge;

import com.releasepilot.exception.BadRequestException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class KnowledgeSearchService {

    static final int MAX_QUERY_CHARS = 300;
    static final int MAX_TOP_K = 10;

    public record Hit(String source, String heading, double score, String text) {
    }

    public record SearchResult(String query, int topK, double minScore, List<Hit> hits, long elapsedMs) {
    }

    private final VectorStore vectorStore;
    private final int defaultTopK;
    private final double defaultMinScore;

    public KnowledgeSearchService(VectorStore vectorStore,
                                  @Value("${knowledge.top-k:4}") int defaultTopK,
                                  @Value("${knowledge.min-score:0.2}") double defaultMinScore) {
        this.vectorStore = vectorStore;
        this.defaultTopK = defaultTopK;
        this.defaultMinScore = defaultMinScore;
    }

    /** minScore <= 0 means "no threshold": return the topK nearest chunks whatever their score. */
    public SearchResult search(String query, Integer topK, Double minScore) {
        long start = System.nanoTime();

        String q = query == null ? "" : query.strip().replaceAll("\\s+", " ");
        if (q.isEmpty()) {
            throw new BadRequestException("QUERY_REQUIRED", "The query must not be blank.");
        }
        if (q.length() > MAX_QUERY_CHARS) {
            throw new BadRequestException("QUERY_TOO_LONG", "The query is longer than " + MAX_QUERY_CHARS + " characters.");
        }
        int k = topK == null ? defaultTopK : topK;
        if (k < 1 || k > MAX_TOP_K) {
            throw new BadRequestException("TOPK_OUT_OF_RANGE", "topK must be between 1 and " + MAX_TOP_K + ".");
        }
        double min = minScore == null ? defaultMinScore : minScore;
        if (min > 1.0) {
            throw new BadRequestException("MIN_SCORE_OUT_OF_RANGE", "minScore must be at most 1.0.");
        }

        SearchRequest.Builder request = SearchRequest.builder()
                .query(q)
                .topK(k)
                .filterExpression(new FilterExpressionBuilder().eq("kind", "knowledge").build());
        if (min <= 0) {
            request.similarityThresholdAll();
        } else {
            request.similarityThreshold(min);
        }

        List<Hit> hits = vectorStore.similaritySearch(request.build()).stream()
                .map(KnowledgeSearchService::toHit)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .toList();

        return new SearchResult(q, k, min, hits, (System.nanoTime() - start) / 1_000_000);
    }

    private static Hit toHit(Document d) {
        Double score = d.getScore();
        return new Hit(meta(d, "source"), meta(d, "heading"), score == null ? 0.0 : score, d.getText());
    }

    private static String meta(Document d, String key) {
        Object value = d.getMetadata().get(key);
        return value == null ? "" : value.toString();
    }
}
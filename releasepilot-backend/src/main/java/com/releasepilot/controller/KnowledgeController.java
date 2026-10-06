package com.releasepilot.controller;

import com.releasepilot.knowledge.KnowledgeIngestionService;
import com.releasepilot.knowledge.KnowledgeIngestionService.IngestionResult;
import com.releasepilot.knowledge.KnowledgeIngestionService.KnowledgeStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.releasepilot.knowledge.KnowledgeSearchService;
import com.releasepilot.knowledge.KnowledgeSearchService.SearchResult;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeIngestionService ingestion;
    private final KnowledgeSearchService search;

    public KnowledgeController(KnowledgeIngestionService ingestion, KnowledgeSearchService search) {
        this.ingestion = ingestion;
        this.search = search;
    }

    /** force=true (default) rebuilds even if nothing changed; force=false is a no-op when the index is current. */
    @PostMapping("/reindex")
    public IngestionResult reindex(@RequestParam(name = "force", defaultValue = "true") boolean force) {
        return ingestion.ingest(force);
    }

    @GetMapping("/status")
    public KnowledgeStatus status() {
        return ingestion.status();
    }

    /** Retrieval without any LLM call. minScore=0 returns the nearest topK chunks whatever their score. */
    @GetMapping("/search")
    public SearchResult search(@RequestParam("q") String q,
                               @RequestParam(name = "topK", required = false) Integer topK,
                               @RequestParam(name = "minScore", required = false) Double minScore) {
        return search.search(q, topK, minScore);
    }
}
package com.releasepilot.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs after EmbeddingStartupCheck. A failure is logged but never stops the app: RAG is a supporting feature. */
@Component
@Order(20)
class KnowledgeStartupIngestion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeStartupIngestion.class);

    private final KnowledgeIngestionService service;
    private final boolean enabled;

    KnowledgeStartupIngestion(KnowledgeIngestionService service,
                              @Value("${knowledge.ingest-on-startup:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[KNOWLEDGE] startup ingestion disabled (knowledge.ingest-on-startup=false)");
            return;
        }
        try {
            service.ingest(false);
        } catch (RuntimeException e) {
            log.error("[KNOWLEDGE] startup ingestion failed; the app keeps running without a fresh index", e);
        }
    }
}
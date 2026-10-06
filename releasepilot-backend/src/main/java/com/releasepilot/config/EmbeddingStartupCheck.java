package com.releasepilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fails fast if the embedding model's real output size differs from the vector_store column size.
 * A mismatch would otherwise surface later as confusing insert/search errors.
 * Runs before knowledge ingestion (Order 20).
 */
@Component
@Order(10)
public class EmbeddingStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStartupCheck.class);

    private final EmbeddingModel embeddingModel;
    private final int expectedDimensions;

    public EmbeddingStartupCheck(EmbeddingModel embeddingModel,
                                 @Value("${spring.ai.vectorstore.pgvector.dimensions}") int expectedDimensions) {
        this.embeddingModel = embeddingModel;
        this.expectedDimensions = expectedDimensions;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        int actual = embeddingModel.embed("dimension check").length;
        long ms = (System.nanoTime() - start) / 1_000_000;
        if (actual != expectedDimensions) {
            throw new IllegalStateException("Embedding model produces " + actual + " dimensions but "
                    + "spring.ai.vectorstore.pgvector.dimensions and the vector_store column are "
                    + expectedDimensions + ". Add a migration and re-index before changing models.");
        }
        log.info("[EMBEDDINGS] {} ready: {} dimensions (first embed took {} ms)",
                embeddingModel.getClass().getSimpleName(), actual, ms);
    }
}
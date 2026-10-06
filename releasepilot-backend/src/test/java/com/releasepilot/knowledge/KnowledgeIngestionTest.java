package com.releasepilot.knowledge;

import com.releasepilot.knowledge.KnowledgeIngestionService.IngestionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Idempotency and transaction behaviour of knowledge ingestion, on the real test database. */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeIngestionTest {

    private static final String KNOWLEDGE = "metadata->>'kind' = 'knowledge'";

    @Autowired KnowledgeIngestionService ingestion;
    @Autowired VectorStore vectorStore;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @Value("${spring.datasource.url}") String url;
    @Value("${knowledge.chunk-max-chars}") int maxChars;
    @Value("${spring.ai.vectorstore.pgvector.dimensions}") int dimensions;

    @BeforeEach
    void onlyTouchTheTestDatabase() {
        assertThat(url).endsWith("_test");
    }

    @AfterEach
    void restoreCleanIndex() {
        ingestion.ingest(true);
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from vector_store where " + KNOWLEDGE, Integer.class);
    }

    private List<String> ids() {
        return jdbc.queryForList("select id::text from vector_store where " + KNOWLEDGE + " order by 1", String.class);
    }

    @Test
    void secondRunIsSkippedAndChangesNothing() {
        IngestionResult first = ingestion.ingest(true);
        assertThat(first.skipped()).isFalse();
        List<String> idsBefore = ids();

        IngestionResult second = ingestion.ingest(false);

        assertThat(second.skipped()).isTrue();
        assertThat(count()).isEqualTo(first.chunks());
        assertThat(ids()).isEqualTo(idsBefore);
    }

    @Test
    void forcedReindexKeepsTheSameIdsAndCreatesNoDuplicates() {
        IngestionResult first = ingestion.ingest(true);
        List<String> idsBefore = ids();

        IngestionResult again = ingestion.ingest(true);

        assertThat(again.skipped()).isFalse();
        assertThat(count()).isEqualTo(first.chunks());
        assertThat(ids()).isEqualTo(idsBefore);
        assertThat(ids()).doesNotHaveDuplicates();
    }

    @Test
    void aMissingRowIsDetectedAndRepaired() {
        IngestionResult first = ingestion.ingest(true);
        jdbc.update("delete from vector_store where id = "
                + "(select id from vector_store where " + KNOWLEDGE + " limit 1)");
        assertThat(count()).isEqualTo(first.chunks() - 1);
        assertThat(ingestion.status().upToDate()).isFalse();

        IngestionResult repaired = ingestion.ingest(false);

        assertThat(repaired.skipped()).isFalse();
        assertThat(count()).isEqualTo(first.chunks());
        assertThat(ingestion.status().upToDate()).isTrue();
    }

    @Test
    void aStaleCorpusHashTriggersReindex() {
        ingestion.ingest(true);
        jdbc.update("update vector_store set metadata = (metadata::jsonb || '{\"corpusHash\":\"stale\"}'::jsonb)::json "
                + "where " + KNOWLEDGE);
        assertThat(ingestion.status().upToDate()).isFalse();

        IngestionResult r = ingestion.ingest(false);

        assertThat(r.skipped()).isFalse();
        Integer stale = jdbc.queryForObject(
                "select count(*) from vector_store where metadata->>'corpusHash' = 'stale'", Integer.class);
        assertThat(stale).isZero();
        assertThat(ingestion.status().upToDate()).isTrue();
    }

    @Test
    void rowsOfOtherKindsAreNeverDeleted() {
        ingestion.ingest(true);
        jdbc.update("insert into vector_store (content, metadata) values ('other', '{\"kind\":\"other\"}'::json)");
        try {
            ingestion.ingest(true);

            Integer other = jdbc.queryForObject(
                    "select count(*) from vector_store where metadata->>'kind' = 'other'", Integer.class);
            assertThat(other).isEqualTo(1);
        } finally {
            jdbc.update("delete from vector_store where metadata->>'kind' = 'other'");
        }
    }

    /**
     * The service deletes the old rows and adds the new ones inside one TransactionTemplate. This proves they
     * really share a transaction: the real add runs, then fails, and the earlier delete must be rolled back.
     */
    @Test
    void failureAfterAddRollsBackTheDelete() {
        int chunks = ingestion.ingest(true).chunks();
        // A marker row of kind 'knowledge': a successful ingest deletes it, a rolled-back one must keep it.
        jdbc.update("insert into vector_store (content, metadata) values "
                + "('marker', '{\"kind\":\"knowledge\",\"source\":\"marker.md\",\"corpusHash\":\"old\"}'::json)");
        assertThat(count()).isEqualTo(chunks + 1);

        VectorStore failingAfterAdd = mock(VectorStore.class, delegatesTo(vectorStore));
        doAnswer(inv -> {
            vectorStore.add(inv.<List<Document>>getArgument(0));   // the real insert happens...
            throw new IllegalStateException("simulated failure after add");   // ...then the transaction fails
        }).when(failingAfterAdd).add(anyList());

        KnowledgeIngestionService broken =
                new KnowledgeIngestionService(failingAfterAdd, jdbc, txManager, maxChars, dimensions);

        assertThatThrownBy(() -> broken.ingest(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated failure");

        assertThat(count()).isEqualTo(chunks + 1);
        Integer marker = jdbc.queryForObject(
                "select count(*) from vector_store where metadata->>'source' = 'marker.md'", Integer.class);
        assertThat(marker).isEqualTo(1);

        // and a normal run afterwards cleans the marker up
        ingestion.ingest(true);
        assertThat(count()).isEqualTo(chunks);
    }
}
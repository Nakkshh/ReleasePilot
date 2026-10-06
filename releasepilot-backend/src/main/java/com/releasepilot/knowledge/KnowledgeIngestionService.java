package com.releasepilot.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    /** Bump when the chunking logic changes, so existing indexes are rebuilt. */
    static final int CHUNKER_VERSION = 1;
    private static final String PATTERN = "classpath*:knowledge/*.md";
    private static final String TABLE = "vector_store"; // matches the Flyway V2 migration and the store's default
    private static final Pattern VALID_FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.md");

    public record IngestionResult(int files, int chunks, boolean skipped, String corpusHash, long elapsedMs) {
    }

    public record SourceCount(String source, int chunks) {
    }

    public record KnowledgeStatus(int indexedChunks, List<SourceCount> sources, int filesOnClasspath,
                                  int chunksOnClasspath, String currentCorpusHash, boolean upToDate) {
    }

    private record SourceFile(String name, String content) {
    }

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager txManager;
    private final int maxChars;
    private final int dimensions;

    public KnowledgeIngestionService(VectorStore vectorStore,
                                     JdbcTemplate jdbc,
                                     PlatformTransactionManager txManager,
                                     @Value("${knowledge.chunk-max-chars:900}") int maxChars,
                                     @Value("${spring.ai.vectorstore.pgvector.dimensions}") int dimensions) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.txManager = txManager;
        this.maxChars = maxChars;
        this.dimensions = dimensions;
    }

    /** Rebuilds the knowledge index. When force is false, does nothing if the index already matches the files. */
    public IngestionResult ingest(boolean force) {
        long start = System.nanoTime();
        List<SourceFile> files = loadFiles();
        List<KnowledgeChunker.Chunk> chunks = chunkAll(files);
        String hash = corpusHash(files);

        if (chunks.isEmpty()) {
            log.warn("[KNOWLEDGE] no markdown files found for {}; nothing indexed", PATTERN);
            return new IngestionResult(files.size(), 0, true, hash, elapsedMs(start));
        }
        if (!force && isUpToDate(hash, chunks.size())) {
            log.info("[KNOWLEDGE] index is up to date ({} chunks from {} files); skipped", chunks.size(), files.size());
            return new IngestionResult(files.size(), chunks.size(), true, hash, elapsedMs(start));
        }

        List<Document> documents = chunks.stream().map(c -> toDocument(c, hash)).toList();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            vectorStore.delete(new FilterExpressionBuilder().eq("kind", "knowledge").build());
            vectorStore.add(documents);
        });

        long ms = elapsedMs(start);
        log.info("[KNOWLEDGE] indexed {} chunks from {} files in {} ms (corpus {})",
                chunks.size(), files.size(), ms, hash.substring(0, 12));
        return new IngestionResult(files.size(), chunks.size(), false, hash, ms);
    }

    public KnowledgeStatus status() {
        List<SourceFile> files = loadFiles();
        List<KnowledgeChunker.Chunk> chunks = chunkAll(files);
        String hash = corpusHash(files);

        List<SourceCount> sources = jdbc.query(
                "select metadata->>'source' as source, count(*) as n from " + TABLE
                        + " where metadata->>'kind' = 'knowledge' group by 1 order by 1",
                (rs, i) -> new SourceCount(rs.getString("source"), rs.getInt("n")));
        int indexed = sources.stream().mapToInt(SourceCount::chunks).sum();

        return new KnowledgeStatus(indexed, sources, files.size(), chunks.size(), hash,
                !chunks.isEmpty() && isUpToDate(hash, chunks.size()));
    }

    private boolean isUpToDate(String hash, int expectedChunks) {
        Integer total = jdbc.queryForObject(
                "select count(*) from " + TABLE + " where metadata->>'kind' = 'knowledge'", Integer.class);
        Integer current = jdbc.queryForObject(
                "select count(*) from " + TABLE + " where metadata->>'kind' = 'knowledge' and metadata->>'corpusHash' = ?",
                Integer.class, hash);
        return total != null && current != null && total == expectedChunks && current == expectedChunks;
    }

    private List<SourceFile> loadFiles() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(PATTERN);
            return java.util.Arrays.stream(resources)
                    .filter(r -> r.getFilename() != null)
                    .map(r -> {
                        String name = r.getFilename();
                        if (!VALID_FILE_NAME.matcher(name).matches()) {
                            throw new IllegalStateException("Knowledge file name '" + name + "' is not allowed. "
                                    + "Use letters, digits, '.', '_' or '-' only, with no leading or trailing spaces.");
                        }
                        try {
                            String text = r.getContentAsString(StandardCharsets.UTF_8)
                                    .replace("\r\n", "\n").replace('\r', '\n');
                            return new SourceFile(name, text);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .sorted(Comparator.comparing(SourceFile::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<KnowledgeChunker.Chunk> chunkAll(List<SourceFile> files) {
        return files.stream().flatMap(f -> KnowledgeChunker.split(f.name(), f.content(), maxChars).stream()).toList();
    }

    /** Covers file contents plus every setting that changes what gets indexed. */
    private String corpusHash(List<SourceFile> files) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(("v" + CHUNKER_VERSION + "|" + maxChars + "|" + dimensions + "\n").getBytes(StandardCharsets.UTF_8));
            for (SourceFile f : files) {
                md.update((f.name() + "\0" + f.content() + "\0").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Deterministic UUID: the vector_store id column is uuid, and the same chunk must get the same id every time. */
    private static Document toDocument(KnowledgeChunker.Chunk c, String hash) {
        String id = UUID.nameUUIDFromBytes((c.source() + "#" + c.index()).getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "knowledge");
        metadata.put("source", c.source());
        metadata.put("title", c.title());
        metadata.put("heading", c.heading());
        metadata.put("chunkIndex", c.index());
        metadata.put("corpusHash", hash);
        return Document.builder().id(id).text(c.text()).metadata(metadata).build();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
-- Phase 9: knowledge-base vector store. Layout follows the Spring AI PgVectorStore documentation.
-- 384 = output dimension of all-MiniLM-L6-v2. Changing the embedding model means a new migration
-- (new dimension) and a full re-index.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE vector_store (
                              id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
                              content   text,
                              metadata  json,
                              embedding vector(384)
);

CREATE INDEX idx_vector_store_embedding ON vector_store USING HNSW (embedding vector_cosine_ops);
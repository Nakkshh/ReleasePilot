CREATE TABLE release_drafts (
                                id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                tag                  VARCHAR(64)  NOT NULL,
                                prerelease           BOOLEAN      NOT NULL DEFAULT FALSE,
                                title                VARCHAR(300) NOT NULL,

                                notes_json           TEXT         NOT NULL,  -- full ReleaseNotesResponse
                                verdict_json         TEXT         NOT NULL,  -- full ReleaseCheckResponse
                                verdict_status       VARCHAR(16)  NOT NULL,  -- READY / NOT_READY / UNKNOWN
                                has_invalid_refs     BOOLEAN      NOT NULL DEFAULT FALSE,
                                baseline             VARCHAR(300),
                                target_sha           VARCHAR(64),

                                status               VARCHAR(16)  NOT NULL,
                                created_at           TIMESTAMPTZ  NOT NULL,
                                expires_at           TIMESTAMPTZ  NOT NULL,

                                decided_at           TIMESTAMPTZ,
                                decided_by           VARCHAR(100),
                                decision_comment     VARCHAR(1000),
                                override_used        BOOLEAN      NOT NULL DEFAULT FALSE,
                                override_reason      VARCHAR(1000),

                                github_release_id    BIGINT,
                                github_release_url   VARCHAR(500),
                                released_at          TIMESTAMPTZ,
                                last_error           TEXT,

                                version              BIGINT       NOT NULL DEFAULT 0,

                                CONSTRAINT chk_release_drafts_status
                                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'RELEASED'))
);

CREATE INDEX idx_release_drafts_status_created ON release_drafts (status, created_at DESC);

-- Backstop: at most one approved/released draft per tag, enforced by the database.
CREATE UNIQUE INDEX uq_release_drafts_active_tag
    ON release_drafts (tag)
    WHERE status IN ('APPROVED', 'RELEASED');
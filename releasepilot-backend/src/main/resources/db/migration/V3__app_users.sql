CREATE TABLE app_users (
                           id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           username      VARCHAR(50)  NOT NULL,
                           password_hash VARCHAR(100) NOT NULL,
                           role          VARCHAR(16)  NOT NULL,
                           enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
                           created_at    TIMESTAMPTZ  NOT NULL,

                           CONSTRAINT uq_app_users_username UNIQUE (username),
                           CONSTRAINT chk_app_users_username_lower CHECK (username = lower(username)),
                           CONSTRAINT chk_app_users_role CHECK (role IN ('ADMIN', 'APPROVER'))
);
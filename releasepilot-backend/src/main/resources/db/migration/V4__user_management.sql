ALTER TABLE release_drafts ADD COLUMN created_by VARCHAR(100);

ALTER TABLE app_users ADD COLUMN password_changed_at TIMESTAMPTZ;
UPDATE app_users SET password_changed_at = created_at;
ALTER TABLE app_users ALTER COLUMN password_changed_at SET NOT NULL;
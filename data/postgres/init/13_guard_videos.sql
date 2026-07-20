-- Videos uploaded by admins for the guard gate display side panel.

CREATE TABLE IF NOT EXISTS guard_videos (
    id            BIGSERIAL PRIMARY KEY,
    file_path     VARCHAR(300) NOT NULL,
    original_name VARCHAR(300) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_guard_videos_uploaded
    ON guard_videos (uploaded_at ASC, id ASC);

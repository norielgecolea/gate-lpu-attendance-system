-- Audio tones uploaded for gate kiosk events (time in/out, error, finance).

CREATE TABLE IF NOT EXISTS gate_tones (
    id            BIGSERIAL PRIMARY KEY,
    file_path     VARCHAR(300) NOT NULL,
    original_name VARCHAR(300) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gate_tones_uploaded
    ON gate_tones (uploaded_at ASC, id ASC);

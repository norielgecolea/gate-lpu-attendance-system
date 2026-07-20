-- Unrecognized RFID / ID taps at a gate (record not found).

CREATE TABLE IF NOT EXISTS tap_error_logs (
    id          BIGSERIAL PRIMARY KEY,
    identifier  VARCHAR(100) NOT NULL,
    location    VARCHAR(100),
    tapped_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tap_error_logs_tapped
    ON tap_error_logs (tapped_at DESC, id DESC);

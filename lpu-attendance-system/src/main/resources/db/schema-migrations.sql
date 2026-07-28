-- Idempotent schema patches applied on every startup before Hibernate validation.
-- Docker init scripts remain the source of truth for fresh database volumes.

CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

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

CREATE TABLE IF NOT EXISTS tap_error_logs (
    id          BIGSERIAL PRIMARY KEY,
    identifier  VARCHAR(100) NOT NULL,
    location    VARCHAR(100),
    tapped_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tap_error_logs_tapped
    ON tap_error_logs (tapped_at DESC, id DESC);

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

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS finance_tagged BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_students_finance_tagged ON students (finance_tagged);

CREATE TABLE IF NOT EXISTS student_audit_events (
    id             BIGSERIAL PRIMARY KEY,
    student_id     BIGINT      NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL,
    actor_user_id  BIGINT REFERENCES users(id),
    actor_username VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_student_audit_action CHECK (action IN ('CREATED'))
);

CREATE INDEX IF NOT EXISTS idx_student_audit_events_student_created
    ON student_audit_events (student_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS employee_audit_events (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT      NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL,
    actor_user_id  BIGINT REFERENCES users(id),
    actor_username VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_employee_audit_action CHECK (action IN ('CREATED'))
);

CREATE INDEX IF NOT EXISTS idx_employee_audit_events_employee_created
    ON employee_audit_events (employee_id, created_at DESC, id DESC);

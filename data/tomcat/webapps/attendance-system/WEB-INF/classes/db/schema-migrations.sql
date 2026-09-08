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

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS lpu_email VARCHAR(255);

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS lpu_email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_students_updated_id
    ON students (updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_employees_updated_id
    ON employees (updated_at ASC, id ASC);

CREATE TABLE IF NOT EXISTS sync_deletion_tombstones (
    id          BIGSERIAL PRIMARY KEY,
    person_type VARCHAR(10) NOT NULL,
    person_id   BIGINT NOT NULL,
    person_no   VARCHAR(50) NOT NULL,
    deleted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sync_tombstone_person_type
        CHECK (person_type IN ('STUDENT', 'EMPLOYEE'))
);

CREATE INDEX IF NOT EXISTS idx_sync_tombstones_deleted
    ON sync_deletion_tombstones (deleted_at ASC, id ASC);

CREATE TABLE IF NOT EXISTS student_audit_events (
    id             BIGSERIAL PRIMARY KEY,
    student_id     BIGINT      NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL,
    actor_user_id  BIGINT REFERENCES users(id),
    actor_username VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE student_audit_events DROP CONSTRAINT IF EXISTS chk_student_audit_action;
ALTER TABLE student_audit_events
    ADD CONSTRAINT chk_student_audit_action
    CHECK (action IN ('CREATED', 'UPDATED', 'PHOTO_UPDATED', 'DELETED'));

CREATE INDEX IF NOT EXISTS idx_student_audit_events_student_created
    ON student_audit_events (student_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_student_audit_events_created
    ON student_audit_events (created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS employee_audit_events (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT      NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    action         VARCHAR(20) NOT NULL,
    actor_user_id  BIGINT REFERENCES users(id),
    actor_username VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE employee_audit_events DROP CONSTRAINT IF EXISTS chk_employee_audit_action;
ALTER TABLE employee_audit_events
    ADD CONSTRAINT chk_employee_audit_action
    CHECK (action IN ('CREATED', 'UPDATED', 'PHOTO_UPDATED', 'DELETED'));

CREATE INDEX IF NOT EXISTS idx_employee_audit_events_employee_created
    ON employee_audit_events (employee_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_employee_audit_events_created
    ON employee_audit_events (created_at DESC, id DESC);

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS kiosk_group VARCHAR(20) NOT NULL DEFAULT 'MAIN_GATES';

ALTER TABLE attendance_events
    ADD COLUMN IF NOT EXISTS kiosk_group VARCHAR(20) NOT NULL DEFAULT 'MAIN_GATES';

ALTER TABLE tap_error_logs
    ADD COLUMN IF NOT EXISTS kiosk_group VARCHAR(20) NOT NULL DEFAULT 'MAIN_GATES';

ALTER TABLE attendance_logs DROP CONSTRAINT IF EXISTS attendance_logs_student_id_attendance_date_key;
DROP INDEX IF EXISTS uq_attendance_employee_date;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_student_date_group
    ON attendance_logs (student_id, attendance_date, kiosk_group)
    WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_employee_date_group
    ON attendance_logs (employee_id, attendance_date, kiosk_group)
    WHERE employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_kiosk_date
    ON attendance_logs (kiosk_group, attendance_date DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_events_kiosk_date
    ON attendance_events (kiosk_group, attendance_date DESC, tapped_at DESC);

CREATE INDEX IF NOT EXISTS idx_tap_error_logs_kiosk_tapped
    ON tap_error_logs (kiosk_group, tapped_at DESC, id DESC);

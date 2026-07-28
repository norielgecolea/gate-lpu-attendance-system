-- LPU Gate Attendance System — full schema (all tables)
-- Safe to re-run: uses IF NOT EXISTS / ON CONFLICT.
--
-- Apply:
--   docker exec -i postgres-db psql -U postgres -d postgres < data/postgres/schema.sql

-- ---------------------------------------------------------------------------
-- users
-- Default accounts (local Docker only — rotate in production):
--   superadmin / SuperAdmin@123
--   guard      / Guard@123      (Gate 1)
--   guard2     / Guard@123      (Gate 2)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    location        VARCHAR(100),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO users (username, password_hash, role, active)
VALUES (
    'superadmin',
    '$2b$10$wLzyCFwyRlwIcB4ZU0L9q.9tnLT9BOlnds9B8x41tpZlFck9d0ukq',
    'SUPERADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password_hash, role, location, active)
VALUES (
    'guard',
    '$2b$10$Z4nIU2XOAczl4gTo8G9bROr3OMdFKQhZEtajEIL7yO0A/NBBABfX2',
    'GUARD',
    'Gate 1',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password_hash, role, location, active)
VALUES (
    'guard2',
    '$2b$10$Z4nIU2XOAczl4gTo8G9bROr3OMdFKQhZEtajEIL7yO0A/NBBABfX2',
    'GUARD',
    'Gate 2',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- ---------------------------------------------------------------------------
-- students
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    student_no      VARCHAR(50)  NOT NULL UNIQUE,
    photo           TEXT,
    rfid            VARCHAR(100),
    birthdate       DATE,
    department      VARCHAR(100) NOT NULL,
    course          VARCHAR(100) NOT NULL,
    school          VARCHAR(100) NOT NULL,
    finance_tagged  BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_students_deleted ON students (deleted);
CREATE INDEX IF NOT EXISTS idx_students_name ON students (name);
CREATE INDEX IF NOT EXISTS idx_students_finance_tagged ON students (finance_tagged);
CREATE INDEX IF NOT EXISTS idx_students_rfid
    ON students (rfid)
    WHERE rfid IS NOT NULL AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- employees
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS employees (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    employee_no     VARCHAR(50)  NOT NULL UNIQUE,
    photo           TEXT,
    rfid            VARCHAR(100),
    birthdate       DATE,
    department      VARCHAR(100),
    position        VARCHAR(100),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employees_deleted ON employees (deleted);
CREATE INDEX IF NOT EXISTS idx_employees_name ON employees (name);
CREATE INDEX IF NOT EXISTS idx_employees_rfid
    ON employees (rfid)
    WHERE rfid IS NOT NULL AND deleted = FALSE;

-- ---------------------------------------------------------------------------
-- attendance_logs (daily summary: exactly one of student_id / employee_id)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_logs (
    id                  BIGSERIAL PRIMARY KEY,
    student_id          BIGINT REFERENCES students(id),
    employee_id         BIGINT REFERENCES employees(id),
    attendance_date     DATE NOT NULL DEFAULT CURRENT_DATE,
    time_in             TIMESTAMPTZ NOT NULL,
    time_out            TIMESTAMPTZ,
    last_action         VARCHAR(20) NOT NULL DEFAULT 'TIME_IN',
    tapped_by_user_id   BIGINT REFERENCES users(id),
    time_in_location    VARCHAR(100),
    time_out_location   VARCHAR(100),
    tap_count           INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, attendance_date)
);

ALTER TABLE attendance_logs DROP CONSTRAINT IF EXISTS chk_attendance_logs_person;
ALTER TABLE attendance_logs
    ADD CONSTRAINT chk_attendance_logs_person
    CHECK (
        (student_id IS NOT NULL AND employee_id IS NULL)
        OR (student_id IS NULL AND employee_id IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_employee_date
    ON attendance_logs (employee_id, attendance_date)
    WHERE employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_updated
    ON attendance_logs (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_date
    ON attendance_logs (attendance_date DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_date_updated_id
    ON attendance_logs (attendance_date DESC, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_student_date
    ON attendance_logs (student_id, attendance_date DESC)
    WHERE student_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_employee_date
    ON attendance_logs (employee_id, attendance_date DESC)
    WHERE employee_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- attendance_events (immutable tap history)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_events (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT REFERENCES students(id),
    employee_id       BIGINT REFERENCES employees(id),
    attendance_date   DATE NOT NULL,
    action            VARCHAR(20) NOT NULL,
    tapped_at         TIMESTAMPTZ NOT NULL,
    location          VARCHAR(100),
    tapped_by_user_id BIGINT REFERENCES users(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_attendance_events_person CHECK (
        (student_id IS NOT NULL AND employee_id IS NULL)
        OR (student_id IS NULL AND employee_id IS NOT NULL)
    ),
    CONSTRAINT chk_attendance_events_action CHECK (action IN ('TIME_IN', 'TIME_OUT'))
);

CREATE INDEX IF NOT EXISTS idx_attendance_events_date_tapped
    ON attendance_events (attendance_date DESC, tapped_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_events_student_date
    ON attendance_events (student_id, attendance_date DESC, tapped_at DESC)
    WHERE student_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_events_employee_date
    ON attendance_events (employee_id, attendance_date DESC, tapped_at DESC)
    WHERE employee_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- app_settings
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------------
-- guard_videos
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- gate_tones
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- tap_error_logs
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tap_error_logs (
    id          BIGSERIAL PRIMARY KEY,
    identifier  VARCHAR(100) NOT NULL,
    location    VARCHAR(100),
    tapped_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tap_error_logs_tapped
    ON tap_error_logs (tapped_at DESC, id DESC);

-- ---------------------------------------------------------------------------
-- student_audit_events (student lifecycle audit)
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- employee_audit_events (employee lifecycle audit)
-- ---------------------------------------------------------------------------
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

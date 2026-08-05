CREATE TABLE IF NOT EXISTS students (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    student_no      VARCHAR(50)  NOT NULL UNIQUE,
    photo           TEXT,
    rfid            VARCHAR(100),
    birthdate       DATE,
    lpu_email       VARCHAR(255),
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

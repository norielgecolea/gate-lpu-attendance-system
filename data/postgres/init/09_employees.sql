-- Employee records, managed like students (soft-delete = inactive).

CREATE TABLE IF NOT EXISTS employees (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    employee_no     VARCHAR(50)  NOT NULL UNIQUE,
    photo           TEXT,
    rfid            VARCHAR(100),
    birthdate       DATE,
    lpu_email       VARCHAR(255),
    department      VARCHAR(100),
    position        VARCHAR(100),
    alarm_marked    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employees_deleted ON employees (deleted);
CREATE INDEX IF NOT EXISTS idx_employees_name ON employees (name);
CREATE INDEX IF NOT EXISTS idx_employees_alarm_marked ON employees (alarm_marked);

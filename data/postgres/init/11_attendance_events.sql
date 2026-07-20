-- Immutable tap history + daily summary counters for reporting.

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS tap_count INTEGER NOT NULL DEFAULT 1;

-- Exactly one person type per daily summary row.
ALTER TABLE attendance_logs DROP CONSTRAINT IF EXISTS chk_attendance_logs_person;
ALTER TABLE attendance_logs
    ADD CONSTRAINT chk_attendance_logs_person
    CHECK (
        (student_id IS NOT NULL AND employee_id IS NULL)
        OR (student_id IS NULL AND employee_id IS NOT NULL)
    );

CREATE INDEX IF NOT EXISTS idx_attendance_logs_date_updated_id
    ON attendance_logs (attendance_date DESC, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_student_date
    ON attendance_logs (student_id, attendance_date DESC)
    WHERE student_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_logs_employee_date
    ON attendance_logs (employee_id, attendance_date DESC)
    WHERE employee_id IS NOT NULL;

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

-- Backfill one synthetic TIME_IN event for existing daily rows that have no events yet.
INSERT INTO attendance_events (
    student_id, employee_id, attendance_date, action, tapped_at, location, tapped_by_user_id, created_at
)
SELECT
    al.student_id,
    al.employee_id,
    al.attendance_date,
    'TIME_IN',
    al.time_in,
    al.time_in_location,
    al.tapped_by_user_id,
    al.created_at
FROM attendance_logs al
WHERE NOT EXISTS (
    SELECT 1
    FROM attendance_events ae
    WHERE ae.attendance_date = al.attendance_date
      AND (
          (al.student_id IS NOT NULL AND ae.student_id = al.student_id)
          OR (al.employee_id IS NOT NULL AND ae.employee_id = al.employee_id)
      )
);

-- Backfill TIME_OUT when the daily summary already closed.
INSERT INTO attendance_events (
    student_id, employee_id, attendance_date, action, tapped_at, location, tapped_by_user_id, created_at
)
SELECT
    al.student_id,
    al.employee_id,
    al.attendance_date,
    'TIME_OUT',
    al.time_out,
    al.time_out_location,
    al.tapped_by_user_id,
    COALESCE(al.updated_at, al.created_at)
FROM attendance_logs al
WHERE al.time_out IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM attendance_events ae
      WHERE ae.attendance_date = al.attendance_date
        AND ae.action = 'TIME_OUT'
        AND (
            (al.student_id IS NOT NULL AND ae.student_id = al.student_id)
            OR (al.employee_id IS NOT NULL AND ae.employee_id = al.employee_id)
        )
  );

UPDATE attendance_logs al
SET tap_count = GREATEST(
    1,
    (
        SELECT COUNT(*)::INTEGER
        FROM attendance_events ae
        WHERE ae.attendance_date = al.attendance_date
          AND (
              (al.student_id IS NOT NULL AND ae.student_id = al.student_id)
              OR (al.employee_id IS NOT NULL AND ae.employee_id = al.employee_id)
          )
    )
);

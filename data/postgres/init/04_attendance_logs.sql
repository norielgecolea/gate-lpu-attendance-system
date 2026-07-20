CREATE TABLE IF NOT EXISTS attendance_logs (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT NOT NULL REFERENCES students(id),
    attendance_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    time_in           TIMESTAMPTZ NOT NULL,
    time_out          TIMESTAMPTZ,
    last_action         VARCHAR(20) NOT NULL DEFAULT 'TIME_IN',
    tapped_by_user_id   BIGINT REFERENCES users(id),
    time_in_location    VARCHAR(100),
    time_out_location   VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (student_id, attendance_date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_updated
    ON attendance_logs (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_attendance_logs_date
    ON attendance_logs (attendance_date DESC);

CREATE INDEX IF NOT EXISTS idx_students_rfid
    ON students (rfid)
    WHERE rfid IS NOT NULL AND deleted = FALSE;

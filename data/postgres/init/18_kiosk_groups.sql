-- Independent attendance venues: Main Gates, Library, Olive Hotel.
-- Existing rows belong to MAIN_GATES. Daily uniqueness is per person + date + group.

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

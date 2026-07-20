-- Allow multiple in/out cycles; final record keeps first time_in and last time_out.
ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS last_action VARCHAR(20);

UPDATE attendance_logs
SET last_action = CASE
    WHEN time_out IS NULL THEN 'TIME_IN'
    ELSE 'TIME_OUT'
END
WHERE last_action IS NULL;

ALTER TABLE attendance_logs
    ALTER COLUMN last_action SET DEFAULT 'TIME_IN';

ALTER TABLE attendance_logs
    ALTER COLUMN last_action SET NOT NULL;

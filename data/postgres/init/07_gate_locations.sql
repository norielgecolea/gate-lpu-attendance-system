-- Gate / location tags for GUARD accounts and attendance time logs.
-- Existing DBs: ADD COLUMN IF NOT EXISTS is safe to re-run.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS location VARCHAR(100);

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS time_in_location VARCHAR(100);

ALTER TABLE attendance_logs
    ADD COLUMN IF NOT EXISTS time_out_location VARCHAR(100);

UPDATE users SET location = 'Gate 1' WHERE username = 'guard' AND (location IS NULL OR location = '');
UPDATE users SET location = 'Gate 2' WHERE username = 'guard2' AND (location IS NULL OR location = '');

-- Finance unsettled-account marker for students.
-- Safe for existing databases.

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS finance_tagged BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_students_finance_tagged ON students (finance_tagged);

-- Superadmin alarm list: marked students/employees ring on monitor and admin dashboards.

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS alarm_marked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS alarm_marked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_students_alarm_marked ON students (alarm_marked);
CREATE INDEX IF NOT EXISTS idx_employees_alarm_marked ON employees (alarm_marked);

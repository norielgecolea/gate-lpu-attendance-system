-- Employees can tap the gate: attendance_logs rows now reference either a
-- student or an employee (exactly one of the two).

ALTER TABLE attendance_logs ALTER COLUMN student_id DROP NOT NULL;
ALTER TABLE attendance_logs ADD COLUMN IF NOT EXISTS employee_id BIGINT REFERENCES employees(id);

-- One log per employee per day (mirrors the student unique constraint).
CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_employee_date
    ON attendance_logs (employee_id, attendance_date)
    WHERE employee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_employees_rfid
    ON employees (rfid)
    WHERE rfid IS NOT NULL AND deleted = FALSE;

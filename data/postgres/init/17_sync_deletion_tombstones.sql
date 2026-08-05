CREATE TABLE IF NOT EXISTS sync_deletion_tombstones (
    id          BIGSERIAL PRIMARY KEY,
    person_type VARCHAR(10) NOT NULL,
    person_id   BIGINT NOT NULL,
    person_no   VARCHAR(50) NOT NULL,
    deleted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sync_tombstone_person_type
        CHECK (person_type IN ('STUDENT', 'EMPLOYEE'))
);

CREATE INDEX IF NOT EXISTS idx_students_updated_id
    ON students (updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_employees_updated_id
    ON employees (updated_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_sync_tombstones_deleted
    ON sync_deletion_tombstones (deleted_at ASC, id ASC);

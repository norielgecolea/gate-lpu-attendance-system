-- Allow nullable department/position (CSV import with partial fields).
-- Safe to re-run on existing databases.

ALTER TABLE employees ALTER COLUMN department DROP NOT NULL;
ALTER TABLE employees ALTER COLUMN position DROP NOT NULL;

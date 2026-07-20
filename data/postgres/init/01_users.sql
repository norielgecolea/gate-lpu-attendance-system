-- Default SUPERADMIN credentials (local Docker only — rotate in production):
--   username: superadmin
--   password: SuperAdmin@123

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    location        VARCHAR(100),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO users (username, password_hash, role, active)
VALUES (
    'superadmin',
    '$2b$10$wLzyCFwyRlwIcB4ZU0L9q.9tnLT9BOlnds9B8x41tpZlFck9d0ukq',
    'SUPERADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- Default GUARD credentials (local Docker only — rotate in production):
--   username: guard
--   password: Guard@123

INSERT INTO users (username, password_hash, role, location, active)
VALUES (
    'guard',
    '$2b$10$Z4nIU2XOAczl4gTo8G9bROr3OMdFKQhZEtajEIL7yO0A/NBBABfX2',
    'GUARD',
    'Gate 1',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

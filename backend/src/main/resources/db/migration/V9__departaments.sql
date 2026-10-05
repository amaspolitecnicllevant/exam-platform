CREATE TABLE departaments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom        VARCHAR(120) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

ALTER TABLE users
    ADD COLUMN departament_id      UUID    REFERENCES departaments(id) ON DELETE SET NULL,
    ADD COLUMN es_cap_departament  BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_users_departament ON users(departament_id);

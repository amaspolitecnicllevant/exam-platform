CREATE TABLE invitacions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    modul_id    UUID NOT NULL REFERENCES moduls(id) ON DELETE CASCADE,
    curs        VARCHAR(10) NOT NULL,
    created_by  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP NOT NULL,
    uses_count  INTEGER NOT NULL DEFAULT 0,
    max_uses    INTEGER,          -- NULL = il·limitat
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

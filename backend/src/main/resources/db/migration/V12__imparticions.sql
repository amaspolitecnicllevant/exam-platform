CREATE TABLE imparticions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    modul_id     UUID        NOT NULL REFERENCES moduls(id)  ON DELETE CASCADE,
    curs         VARCHAR(10) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (professor_id, modul_id, curs)
);

CREATE INDEX idx_imparticions_professor ON imparticions(professor_id);
CREATE INDEX idx_imparticions_modul     ON imparticions(modul_id);

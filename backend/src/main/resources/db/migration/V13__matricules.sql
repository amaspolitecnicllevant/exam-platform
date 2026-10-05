CREATE TABLE matricules (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    alumne_id  UUID        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    cicle_id   UUID        NOT NULL REFERENCES cicles(id)  ON DELETE CASCADE,
    curs       VARCHAR(10) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (alumne_id, cicle_id, curs)
);

CREATE INDEX idx_matricules_alumne ON matricules(alumne_id);
CREATE INDEX idx_matricules_cicle  ON matricules(cicle_id);

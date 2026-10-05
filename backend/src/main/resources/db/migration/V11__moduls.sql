CREATE TABLE moduls (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    codi       VARCHAR(20)  NOT NULL UNIQUE,
    nom        VARCHAR(120) NOT NULL,
    cicle_id   UUID         NOT NULL REFERENCES cicles(id) ON DELETE CASCADE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_moduls_cicle ON moduls(cicle_id);

CREATE TABLE cicles (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    codi           VARCHAR(20)  NOT NULL UNIQUE,
    nom            VARCHAR(120) NOT NULL,
    departament_id UUID         NOT NULL REFERENCES departaments(id) ON DELETE CASCADE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cicles_departament ON cicles(departament_id);

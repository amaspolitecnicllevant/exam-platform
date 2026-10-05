-- Eliminem la relació simple 1:N professor→departament i la substituïm per N:M
ALTER TABLE users DROP COLUMN IF EXISTS departament_id;
ALTER TABLE users DROP COLUMN IF EXISTS es_cap_departament;

CREATE TABLE professor_departaments (
    professor_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    departament_id UUID NOT NULL REFERENCES departaments(id) ON DELETE CASCADE,
    es_cap         BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (professor_id, departament_id)
);

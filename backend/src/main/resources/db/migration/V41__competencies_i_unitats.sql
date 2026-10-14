-- Avaluació per competències (CO) en lloc de resultats d'aprenentatge (RA).
-- Cada mòdul té competències i unitats de treball (UT); cada UT treballa una o més competències,
-- i cada examen s'associa a les UT que avalua.

CREATE TABLE competencies (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    modul_id   UUID         NOT NULL REFERENCES moduls(id) ON DELETE CASCADE,
    codi       VARCHAR(20)  NOT NULL,
    descripcio TEXT         NOT NULL DEFAULT '',
    ordre      INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_competencia_modul_codi UNIQUE (modul_id, codi)
);

CREATE TABLE unitats_treball (
    id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    modul_id UUID         NOT NULL REFERENCES moduls(id) ON DELETE CASCADE,
    codi     VARCHAR(20)  NOT NULL,
    nom      VARCHAR(200) NOT NULL DEFAULT '',
    ordre    INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_unitat_modul_codi UNIQUE (modul_id, codi)
);

CREATE TABLE unitat_competencies (
    unitat_id      UUID NOT NULL REFERENCES unitats_treball(id) ON DELETE CASCADE,
    competencia_id UUID NOT NULL REFERENCES competencies(id)   ON DELETE CASCADE,
    PRIMARY KEY (unitat_id, competencia_id)
);

CREATE TABLE exam_unitats (
    exam_id   UUID NOT NULL REFERENCES exams(id)           ON DELETE CASCADE,
    unitat_id UUID NOT NULL REFERENCES unitats_treball(id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, unitat_id)
);

CREATE INDEX idx_competencies_modul ON competencies(modul_id);
CREATE INDEX idx_unitats_modul      ON unitats_treball(modul_id);
CREATE INDEX idx_exam_unitats_unitat ON exam_unitats(unitat_id);

-- La pregunta guarda el codi de la competència (no una clau forana): en importar el .md encara no se sap el mòdul.
ALTER TABLE questions RENAME COLUMN ra TO competencia;

-- Els exàmens existents: RA1 → CO1, RA2 → CO2… (en els casos actuals cada competència equival a un RA).
-- Si la unitat o el format no segueixen aquest patró, el valor es deixa tal com és.
UPDATE questions
   SET competencia = 'CO' || substring(btrim(competencia) from '^[Rr][Aa]\s*([0-9]+)$')
 WHERE competencia ~ '^\s*[Rr][Aa]\s*[0-9]+\s*$';

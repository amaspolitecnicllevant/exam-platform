-- Ordinadors de les aules i el seu estat, segons els informes que ells mateixos envien periòdicament.
-- L'aplicació no es connecta als ordinadors ni en guarda credencials: només rep dades.
CREATE TABLE equips_aula (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aula_id           UUID         NOT NULL REFERENCES aules(id) ON DELETE CASCADE,
    nom               VARCHAR(100) NOT NULL,
    ip                VARCHAR(45)  NOT NULL,
    darrer_informe    TIMESTAMP    NOT NULL,
    integritat        TEXT,
    integritat_resum  VARCHAR(64),
    arriba_plataforma BOOLEAN,
    arriba_isard      BOOLEAN,
    navegador         VARCHAR(200),
    disc_lliure_mb    INTEGER,
    arrencada         TIMESTAMP,
    usuaris_dins      INTEGER,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_equips_aula_nom UNIQUE (aula_id, nom)
);
CREATE INDEX idx_equips_aula_aula ON equips_aula (aula_id);

-- Estat de referència (un de sol, perquè els ordinadors són clons): amb què es compara cada ordinador.
CREATE TABLE equips_referencia (
    id               SMALLINT PRIMARY KEY CHECK (id = 1),
    integritat       TEXT         NOT NULL,
    integritat_resum VARCHAR(64)  NOT NULL,
    origen_nom       VARCHAR(100),
    fixada_per       UUID REFERENCES users(id) ON DELETE SET NULL,
    fixada_el        TIMESTAMP    NOT NULL DEFAULT now()
);

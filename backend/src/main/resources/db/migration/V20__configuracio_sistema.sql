CREATE TABLE configuracio_sistema (
    id                    INTEGER PRIMARY KEY DEFAULT 1,
    nom_centre            VARCHAR(200) NOT NULL DEFAULT 'SEDEX',
    logo_base64           TEXT,
    logo_mime             VARCHAR(50),
    color_marca           VARCHAR(7)  NOT NULL DEFAULT '#8b1a4a',
    curs_actiu            VARCHAR(10) NOT NULL DEFAULT '2026-27',
    durada_defecte        INTEGER     NOT NULL DEFAULT 90,
    penalitzacio_defecte  NUMERIC(4,2) NOT NULL DEFAULT 0,
    focus_loss_threshold  INTEGER     NOT NULL DEFAULT 5,
    grace_period_seconds  INTEGER     NOT NULL DEFAULT 0,
    dominis_oauth         VARCHAR(500) NOT NULL DEFAULT 'politecnicllevant.cat',
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO configuracio_sistema (id) VALUES (1);

-- Preguntes de lliurament de fitxer (Word, Excel, Packet Tracer…): formats admesos per pregunta
-- i fitxer pujat per l'alumne a la resposta (un per resposta).
ALTER TABLE questions ADD COLUMN formats_permesos VARCHAR(200);

ALTER TABLE answers
    ADD COLUMN fitxer_nom      VARCHAR(255),
    ADD COLUMN fitxer_ruta     VARCHAR(500),
    ADD COLUMN fitxer_mida     BIGINT,
    ADD COLUMN fitxer_sha256   VARCHAR(64),
    ADD COLUMN fitxer_pujat_el TIMESTAMP;

-- Interruptor global: l'administrador pot desactivar la pujada de fitxers dels alumnes
ALTER TABLE configuracio_sistema ADD COLUMN pujada_fitxers_activa BOOLEAN NOT NULL DEFAULT TRUE;

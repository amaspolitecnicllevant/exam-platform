-- Notes aplicades a partir d'una revisió amb IA importada pel professor: es desa la nota anterior, la
-- justificació de la IA i quan es va aplicar. És informació només per al professor (mai arriba a l'alumne).
ALTER TABLE answers ADD COLUMN revisio_ia_el TIMESTAMP;
ALTER TABLE answers ADD COLUMN revisio_ia_nota_abans NUMERIC(4,2);
ALTER TABLE answers ADD COLUMN revisio_ia_justificacio TEXT;

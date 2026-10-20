-- Un examen pot ser un examen o una pràctica, i cada unitat de treball diu quin % de la seva nota és de la part d'exàmens
-- (la resta, de pràctiques). Sense % definit (NULL), exàmens i pràctiques es compten junts pels seus punts.
ALTER TABLE exams ADD COLUMN tipus VARCHAR(10) NOT NULL DEFAULT 'EXAMEN' CHECK (tipus IN ('EXAMEN', 'PRACTICA'));
ALTER TABLE unitats_treball ADD COLUMN pes_examens INT CHECK (pes_examens BETWEEN 0 AND 100);

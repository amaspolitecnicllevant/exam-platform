-- La programació (unitats de treball i competències de mòdul) i el tipus examen/pràctica surten d'aquesta aplicació.
-- Les competències de les preguntes (questions.competencia) i les que declara cada examen (exam_competencies) es conserven
-- com a codis lliures.
DROP TABLE IF EXISTS exam_unitats;
DROP TABLE IF EXISTS unitat_competencies;
DROP TABLE IF EXISTS unitats_treball;
DROP TABLE IF EXISTS competencies;
ALTER TABLE exams DROP COLUMN IF EXISTS tipus;

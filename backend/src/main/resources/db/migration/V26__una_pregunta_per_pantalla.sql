-- Mode d'examen amb una pregunta per pantalla (dificulta copiar entre alumnes)
ALTER TABLE exams ADD COLUMN una_pregunta_per_pantalla BOOLEAN NOT NULL DEFAULT FALSE;

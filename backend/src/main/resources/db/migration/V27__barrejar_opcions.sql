-- Permet fixar l'ordre de les opcions d'una pregunta de test (p. ex. "Totes les anteriors")
ALTER TABLE questions ADD COLUMN barrejar_opcions BOOLEAN NOT NULL DEFAULT TRUE;

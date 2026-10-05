-- Preguntes on es poden fer servir apunts (en paper): l'informe de còpies hi aplica un llindar propi
ALTER TABLE questions ADD COLUMN amb_apunts BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE configuracio_sistema ADD COLUMN copies_llindar_apunts INTEGER NOT NULL DEFAULT 95;

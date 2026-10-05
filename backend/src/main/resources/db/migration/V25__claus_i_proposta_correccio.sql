-- Conceptes clau per proposar la nota de preguntes de text (bloc :::clau)
ALTER TABLE questions ADD COLUMN claus TEXT;

-- Motius de la nota proposada (una línia per motiu)
ALTER TABLE answers ADD COLUMN auto_feedback TEXT;

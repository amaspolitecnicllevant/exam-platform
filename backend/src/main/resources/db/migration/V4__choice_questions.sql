-- Opcions per a preguntes de tipus CHOICE (text separat per \n)
ALTER TABLE questions ADD COLUMN choices TEXT;
-- Resposta correcta: lletra sola (a / b / c / d)
ALTER TABLE questions ADD COLUMN correct_choice VARCHAR(10);

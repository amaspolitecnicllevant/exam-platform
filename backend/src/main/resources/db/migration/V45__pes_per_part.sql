-- Pes (%) de cada part (secció) de l'examen sobre la nota total. Només té valor a les files de tipus SECTION;
-- si cap secció en té, l'examen puntua com sempre (cada pregunta amb els seus punts).
ALTER TABLE questions ADD COLUMN pes_part NUMERIC(5,2);

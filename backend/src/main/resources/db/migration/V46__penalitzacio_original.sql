-- Penalització del test que tenia l'examen quan es van fer les primeres sessions, abans que el professor la canviés
-- a posteriori (null = no s'ha canviat). Permet mostrar la nota original i tornar als valors inicials.
ALTER TABLE exams ADD COLUMN penalitzacio_original NUMERIC(5,4);

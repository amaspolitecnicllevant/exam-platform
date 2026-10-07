-- Examen només per a alumnes concrets: sense la marca, tots els matriculats al mòdul hi poden entrar;
-- amb ella, només els alumnes que tenen una sessió assignada (el professor en pot afegir o treure).
ALTER TABLE exams ADD COLUMN restringit BOOLEAN NOT NULL DEFAULT FALSE;

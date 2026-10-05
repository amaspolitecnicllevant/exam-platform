-- Les sessions creades per avançat (assignar a un grup, exàmens programats) no han començat:
-- started_at queda buit fins que l'alumne obre l'examen, i el rellotge compta des d'aquell moment.
ALTER TABLE exam_sessions ALTER COLUMN started_at DROP NOT NULL;
ALTER TABLE exam_sessions ALTER COLUMN started_at DROP DEFAULT;

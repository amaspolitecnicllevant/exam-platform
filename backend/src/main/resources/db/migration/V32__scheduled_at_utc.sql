-- Fins ara, l'hora de programació que escrivia el professor (hora local) es desava tal qual i el
-- servidor la interpretava com a UTC: l'examen s'activava 1 o 2 hores tard. Ara l'API rep i envia
-- instants UTC. Aquí es passen a UTC les hores ja desades que va escriure un professor (minuts
-- exactes); les que va posar "Reobrir accés" (amb segons) ja eren UTC.
UPDATE exams
SET scheduled_at = (scheduled_at AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'UTC'
WHERE scheduled_at IS NOT NULL
  AND scheduled_at = date_trunc('minute', scheduled_at);

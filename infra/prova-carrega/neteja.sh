#!/usr/bin/env bash
# Esborra TOTES les dades de la prova de càrrega: els exàmens «PROVA DE CÀRREGA…», les seves sessions i respostes,
# els fitxers pujats al disc, els alumnes carrega-NNN@prova.invalid, l'administrador de prova i els seus registres d'activitat.
# No toca res més. Cal executar-lo a la màquina on corre l'aplicació, des de infra/:
#     ./prova-carrega/neteja.sh            mostra què esborraria (no canvia res)
#     ./prova-carrega/neteja.sh --esborra  ho esborra de debò
set -euo pipefail
cd "${INFRA_DIR:-$(dirname "$0")/..}"

PSQL='psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
sql() { docker compose exec -T db sh -c "$PSQL -tA" </dev/stdin; }
EX="(SELECT id FROM exams WHERE title LIKE 'PROVA DE CÀRREGA%')"
US="(SELECT id FROM users WHERE email LIKE 'carrega-%@prova.invalid')"

echo "== Dades de prova trobades"
sql <<SQL
SELECT 'exàmens: '   || count(*) FROM exams WHERE title LIKE 'PROVA DE CÀRREGA%';
SELECT 'sessions: '  || count(*) FROM exam_sessions WHERE exam_id IN $EX;
SELECT 'respostes: ' || count(*) FROM answers WHERE session_id IN (SELECT id FROM exam_sessions WHERE exam_id IN $EX);
SELECT 'usuaris: '   || count(*) FROM users WHERE email LIKE 'carrega-%@prova.invalid';
SQL

if [ "${1:-}" != "--esborra" ]; then
  echo; echo "(Només s'ha mostrat. Per esborrar-ho: $0 --esborra)"; exit 0
fi

echo "== Fitxers del disc"
SESSIONS=$(sql <<SQL
SELECT id FROM exam_sessions WHERE exam_id IN $EX;
SQL
)
PREGUNTES=$(sql <<SQL
SELECT id FROM questions WHERE exam_id IN $EX;
SQL
)
# S'esborren des del contenidor del backend: els fitxers els va crear ell i en té els permisos
for id in $SESSIONS; do docker compose exec -T backend rm -rf "/opt/exam-files/answers/$id" </dev/null; done
for id in $PREGUNTES; do docker compose exec -T backend rm -rf "/opt/exam-files/questions/$id" </dev/null; done
echo "  esborrats els fitxers de $(echo "$SESSIONS" | grep -c . || true) sessions"

echo "== Base de dades"
sql <<SQL
BEGIN;
DELETE FROM audit_log WHERE user_id IN $US;
DELETE FROM exam_sessions WHERE exam_id IN $EX;
DELETE FROM exams WHERE title LIKE 'PROVA DE CÀRREGA%';
DELETE FROM users WHERE email LIKE 'carrega-%@prova.invalid';
COMMIT;
SQL
echo "== Comprovació"
sql <<SQL
SELECT 'exàmens restants: ' || count(*) FROM exams WHERE title LIKE 'PROVA DE CÀRREGA%';
SELECT 'usuaris de prova restants: ' || count(*) FROM users WHERE email LIKE 'carrega-%@prova.invalid';
SQL
echo "Fet."

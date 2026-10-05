#!/usr/bin/env bash
# Restaura una còpia de seguretat de la plataforma. S'executa a la màquina (no dins de cap contenidor).
#
#   ./restaura.sh --prova ../../backups/bd/examplatform-AAAAMMDD-HHMMSS.dump
#       Verifica la còpia: la restaura en un PostgreSQL temporal, mostra el nombre de files per taula
#       i el compara amb la BD en marxa. No toca res de producció.
#
#   ./restaura.sh ../../backups/bd/examplatform-….dump [../../backups/fitxers/exam-files-….tar.gz]
#       Restaura de debò: fa abans una còpia de l'estat actual, atura el frontend i el backend,
#       substitueix la BD (i els fitxers de dades, si s'indiquen) i ho torna a engegar.
set -euo pipefail

cd "$(dirname "$0")/.."   # directori infra/
DB_CONTAINER="${DB_CONTAINER:-infra-db-1}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-infra-backend-1}"
FRONTEND_CONTAINER="${FRONTEND_CONTAINER:-infra-frontend-1}"
BACKUP_CONTAINER="${BACKUP_CONTAINER:-infra-backup-1}"
PG_IMAGE="postgres:16-alpine"

mode=real
if [[ "${1:-}" == "--prova" ]]; then mode=prova; shift; fi
DUMP="${1:-}"
FITXERS="${2:-}"
[[ -n "$DUMP" && -f "$DUMP" ]] || { echo "Ús: $0 [--prova] <còpia.dump> [fitxers.tar.gz]" >&2; exit 2; }

verifica() {   # comprova la suma SHA-256 desada al costat del fitxer
  local f="$1"
  if [[ -f "$f.sha256" ]]; then
    (cd "$(dirname "$f")" && sha256sum -c --quiet "$(basename "$f").sha256") \
      || { echo "✗ La suma de verificació de $f no coincideix: el fitxer és corrupte." >&2; exit 1; }
    echo "✓ Suma de verificació correcta: $(basename "$f")"
  else
    echo "⚠ No hi ha suma de verificació per a $(basename "$f")"
  fi
}

compta_files() {   # $1 = contenidor, $2 = usuari, $3 = BD
  docker exec "$1" psql -U "$2" -d "$3" -Atc "
    SELECT table_name || '=' || (xpath('/row/c/text()',
           query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
    ORDER BY table_name"
}

verifica "$DUMP"
[[ -n "$FITXERS" ]] && verifica "$FITXERS"

DB_USER=$(docker exec "$DB_CONTAINER" sh -c 'echo "$POSTGRES_USER"')
DB_NAME=$(docker exec "$DB_CONTAINER" sh -c 'echo "$POSTGRES_DB"')

if [[ "$mode" == "prova" ]]; then
  temp="restaura-prova-$$"
  echo "→ Restaurant en un PostgreSQL temporal ($temp)…"
  docker run -d --rm --name "$temp" -e POSTGRES_USER=prova -e POSTGRES_PASSWORD=prova -e POSTGRES_DB=prova "$PG_IMAGE" >/dev/null
  trap 'docker rm -f "$temp" >/dev/null 2>&1 || true' EXIT
  for _ in $(seq 1 30); do docker exec "$temp" pg_isready -U prova -d prova >/dev/null 2>&1 && break; sleep 1; done
  sleep 2
  docker exec -i "$temp" pg_restore -U prova -d prova --no-owner --no-privileges --exit-on-error < "$DUMP"
  echo "✓ La còpia es restaura sense errors."
  echo
  echo "Files per taula (còpia · BD en marxa):"
  join -t= -a1 -a2 -e '—' -o 0,1.2,2.2 \
    <(compta_files "$temp" prova prova | sort) \
    <(compta_files "$DB_CONTAINER" "$DB_USER" "$DB_NAME" | sort) \
    | awk -F= '{ printf "  %-28s %8s · %s\n", $1, $2, $3 }'
  echo
  echo "(Les diferències són normals si la BD ha canviat des de la còpia.)"
  exit 0
fi

echo "ATENCIÓ: això substituirà la base de dades en marxa ($DB_NAME) per la còpia:"
echo "  $DUMP"
[[ -n "$FITXERS" ]] && echo "  i els fitxers de dades per: $FITXERS"
read -r -p "Escriu RESTAURA per continuar: " resposta
[[ "$resposta" == "RESTAURA" ]] || { echo "Cancel·lat."; exit 1; }

if docker ps --format '{{.Names}}' | grep -qx "$BACKUP_CONTAINER"; then
  echo "→ Còpia de l'estat actual abans de restaurar (per poder-ho desfer)…"
  docker exec "$BACKUP_CONTAINER" /backup/fes-copia.sh
fi

echo "→ Aturant el frontend i el backend…"
docker stop "$FRONTEND_CONTAINER" "$BACKEND_CONTAINER" >/dev/null 2>&1 || true

echo "→ Substituint la base de dades…"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
  -c "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE)" \
  -c "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\""
docker exec -i "$DB_CONTAINER" pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --exit-on-error < "$DUMP"
echo "✓ Base de dades restaurada."

if [[ -n "$FITXERS" ]]; then
  echo "→ Restaurant els fitxers de dades…"
  # Es fa amb un contenidor temporal perquè /opt/exam-files és del contenidor del backend
  docker run --rm -i -v /opt/exam-files:/opt/exam-files alpine:3 \
    sh -c 'rm -rf /opt/exam-files/* && tar -xzf - -C /opt' < "$FITXERS"
  echo "✓ Fitxers de dades restaurats."
fi

echo "→ Engegant el backend i el frontend…"
if docker start "$BACKEND_CONTAINER" "$FRONTEND_CONTAINER" >/dev/null; then
  echo "✓ Restauració acabada."
else
  echo "⚠ La BD s'ha restaurat, però no s'han pogut engegar $BACKEND_CONTAINER i $FRONTEND_CONTAINER." >&2
  echo "  Engega'ls amb: docker compose up -d backend frontend" >&2
  exit 1
fi

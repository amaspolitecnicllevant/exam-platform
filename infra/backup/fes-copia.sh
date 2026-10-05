#!/bin/sh
# Còpia de seguretat de la plataforma: BD (pg_dump) + fitxers de dades de les preguntes.
# S'executa dins del contenidor "backup" (docker compose exec backup /backup/fes-copia.sh per fer-ne una ara).
#
# Si BACKUP_REMOT=1, cada còpia es replica també a /backups-remot (una carpeta compartida muntada
# al contenidor). La carpeta ha de contenir el fitxer .carpeta-copies-examens: si no hi és, es
# considera que no està muntada (i no s'hi copia res, per no omplir un directori local buit).
set -eu

DESTI=/backups
REMOT=/backups-remot
MARCADOR=.carpeta-copies-examens
RETENCIO_DIES="${BACKUP_RETENCIO_DIES:-30}"
ARA=$(date +%Y%m%d-%H%M%S)
umask 077   # contenen dades dels alumnes: només les pot llegir el propietari

mkdir -p "$DESTI/bd" "$DESTI/fitxers"
ESTAT="$DESTI/estat.json"

# Estat de la còpia remota (s'afegeix a estat.json)
REMOT_CONFIGURAT=false
REMOT_RESULTAT=null
REMOT_MISSATGE=""

escriu_estat() {   # $1=ok|error  $2=missatge  $3=bytes
  cat > "$ESTAT.tmp" <<JSON
{"data": "$(date -Iseconds)", "resultat": "$1", "missatge": "$2", "bytes": ${3:-0}, "retencioDies": $RETENCIO_DIES,
 "remot": {"configurat": $REMOT_CONFIGURAT, "resultat": $REMOT_RESULTAT, "missatge": "$REMOT_MISSATGE"}}
JSON
  mv "$ESTAT.tmp" "$ESTAT"
  chmod 644 "$ESTAT"   # l'estat no té dades personals: el backend el llegeix per mostrar-lo
}

falla() {
  echo "[copia] ERROR: $1" >&2
  escriu_estat error "$1" 0
  exit 1
}

# Copia un fitxer (i la seva suma) a la carpeta remota i verifica que ha arribat sencer
replica() {   # $1 = fitxer local, $2 = subcarpeta remota
  desti="$REMOT/$2"
  mkdir -p "$desti"
  cp "$1" "$desti/$(basename "$1").part"
  cp "$1.sha256" "$desti/$(basename "$1").sha256"
  mv "$desti/$(basename "$1").part" "$desti/$(basename "$1")"
  (cd "$desti" && sha256sum -c "$(basename "$1").sha256" > /dev/null)   # busybox: sense --quiet
}

echo "[copia] $(date -Iseconds) inici"

# 1. Base de dades (format comprimit de pg_dump; es restaura amb pg_restore)
DUMP="$DESTI/bd/examplatform-$ARA.dump"
pg_dump -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -Fc -f "$DUMP.part" || falla "pg_dump ha fallat"
# Comprova que el fitxer és una còpia llegible abans de donar-la per bona
pg_restore --list "$DUMP.part" > /dev/null || falla "la còpia de la BD no és llegible"
mv "$DUMP.part" "$DUMP"
(cd "$DESTI/bd" && sha256sum "$(basename "$DUMP")" > "$(basename "$DUMP").sha256")

# 2. Fitxers de dades de les preguntes
FITX="$DESTI/fitxers/exam-files-$ARA.tar.gz"
tar -czf "$FITX.part" -C /opt exam-files || falla "no s'han pogut copiar els fitxers de dades"
mv "$FITX.part" "$FITX"
(cd "$DESTI/fitxers" && sha256sum "$(basename "$FITX")" > "$(basename "$FITX").sha256")

# 3. Retenció local
find "$DESTI/bd" "$DESTI/fitxers" -type f -mtime +"$RETENCIO_DIES" -print -delete | sed 's/^/[copia] esborrada: /'

# 4. Còpia a la carpeta compartida
if [ "${BACKUP_REMOT:-0}" = "1" ]; then
  REMOT_CONFIGURAT=true
  if [ ! -f "$REMOT/$MARCADOR" ]; then
    REMOT_RESULTAT='"error"'
    REMOT_MISSATGE="La carpeta compartida no està muntada (falta $MARCADOR)"
  elif replica "$DUMP" bd && replica "$FITX" fitxers; then
    find "$REMOT/bd" "$REMOT/fitxers" -type f -mtime +"$RETENCIO_DIES" -print -delete 2>/dev/null \
      | sed 's/^/[copia] esborrada (remot): /' || true
    REMOT_RESULTAT='"ok"'
    REMOT_MISSATGE="Còpia replicada i verificada a la carpeta compartida"
  else
    REMOT_RESULTAT='"error"'
    REMOT_MISSATGE="No s'ha pogut copiar o verificar la còpia a la carpeta compartida"
  fi
  echo "[copia] carpeta compartida: $REMOT_MISSATGE"
fi

BYTES=$(( $(stat -c %s "$DUMP") + $(stat -c %s "$FITX") ))
escriu_estat ok "Còpia correcta: $(basename "$DUMP") i $(basename "$FITX")" "$BYTES"
echo "[copia] $(date -Iseconds) fet ($BYTES bytes)"
[ "$REMOT_RESULTAT" != '"error"' ]   # si la còpia remota ha fallat, acaba amb error

#!/usr/bin/env bash
# Renova el certificat HTTPS del servidor abans que caduqui, reutilitzant l'autoritat del centre.
#
#   ./tls/renova-certificat.sh              renova només si queden menys de 60 dies
#   ./tls/renova-certificat.sh --prova      només mostra l'estat, no canvia res
#   ./tls/renova-certificat.sh --forca      renova ara, encara que no calgui
#   ./tls/renova-certificat.sh --dies 90    canvia el llindar de dies
#
# - Les IP/noms del certificat nou són els del certificat actual (no cal recordar-los).
# - L'autoritat (ca.crt/ca.key) NO es toca: els ordinadors no s'han de tornar a configurar.
# - Si falta ca.key, s'atura: genera-certificats.sh en crearia una de nova i caldria reinstal·lar-la
#   a tots els ordinadors.
# - nginx recarrega el certificat sense tallar les connexions (reload), així que es pot executar
#   encara que hi hagi alumnes fent un examen.
#
# Planificació setmanal (crontab -e, amb un usuari del grup docker; ajusta la ruta):
#   0 4 * * 1  /home/USUARI/exam-platform/infra/tls/renova-certificat.sh >> /home/USUARI/renova-certificat.log 2>&1
set -euo pipefail
cd "$(dirname "$0")"

DIES=60
FORCA=0
PROVA=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --forca) FORCA=1 ;;
    --prova) PROVA=1 ;;
    --dies)
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { echo "--dies requereix un número" >&2; exit 2; }
      DIES="$2"; shift ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "Opció desconeguda: $1 (vegeu --help)" >&2; exit 2 ;;
  esac
  shift
done

DIR=certs
INFRA="$(cd .. && pwd)"
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

[[ -f "$DIR/server.crt" ]] || { log "ERROR: no hi ha $DIR/server.crt. Genera'l amb ./tls/genera-certificats.sh <IP>"; exit 1; }
[[ -f "$DIR/ca.key" && -f "$DIR/ca.crt" ]] || { log "ERROR: falta l'autoritat ($DIR/ca.crt o $DIR/ca.key). No es renova per no crear-ne una de nova."; exit 1; }

caduca_server=$(openssl x509 -in "$DIR/server.crt" -noout -enddate | cut -d= -f2)
caduca_ca=$(openssl x509 -in "$DIR/ca.crt" -noout -enddate | cut -d= -f2)
dies_server=$(( ( $(date -d "$caduca_server" +%s) - $(date +%s) ) / 86400 ))
dies_ca=$(( ( $(date -d "$caduca_ca" +%s) - $(date +%s) ) / 86400 ))

log "Certificat del servidor: caduca el $caduca_server (queden $dies_server dies)"
log "Autoritat del centre:    caduca el $caduca_ca (queden $dies_ca dies)"
if (( dies_ca < 365 )); then
  log "AVÍS: l'autoritat caduca d'aquí a menys d'un any. Caldrà crear-ne una de nova i reinstal·lar ca.crt als ordinadors."
fi

if (( PROVA )); then exit 0; fi
if (( ! FORCA && dies_server >= DIES )); then
  log "No cal renovar (llindar: $DIES dies)."
  exit 0
fi

# IP i noms del certificat actual: "IP Address:10.0.0.1, DNS:exemple" -> "10.0.0.1 exemple"
mapfile -t NOMS < <(openssl x509 -in "$DIR/server.crt" -noout -ext subjectAltName \
  | tail -n +2 | tr ',' '\n' | sed -E 's/^[[:space:]]*(IP Address|DNS)://' | sed '/^[[:space:]]*$/d')
(( ${#NOMS[@]} > 0 )) || { log "ERROR: el certificat actual no té subjectAltName; torna'l a generar a mà."; exit 1; }
log "Es renova per a: ${NOMS[*]}"

# Còpia del certificat anterior per poder tornar enrere
ANT="$DIR/anteriors/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$ANT"
cp -p "$DIR/server.crt" "$DIR/server.key" "$ANT/"

./genera-certificats.sh "${NOMS[@]}"

# nginx llegeix el certificat en arrencar: es recarrega sense tallar connexions
if ( cd "$INFRA" && docker compose exec -T frontend nginx -s reload ) 2>&1; then
  log "nginx recarregat."
else
  log "AVÍS: no s'ha pogut recarregar nginx. Fes-ho a mà: cd $INFRA && docker compose up -d --force-recreate frontend"
  exit 1
fi

# Comprovació: el servidor ha de presentar el certificat nou i verificar amb la nostra autoritat
PORT=$(grep -E '^HTTPS_PORT=' "$INFRA/.env" 2>/dev/null | tail -1 | cut -d= -f2 || true)
PORT=${PORT:-3443}
if curl -sS -o /dev/null --max-time 10 --cacert "$DIR/ca.crt" "https://${NOMS[0]}:$PORT/"; then
  log "OK: https://${NOMS[0]}:$PORT verificat amb el certificat nou (caduca el $(openssl x509 -in "$DIR/server.crt" -noout -enddate | cut -d= -f2))."
else
  log "ERROR: la comprovació HTTPS ha fallat. El certificat anterior és a $ANT/ (restaura'l i recarrega nginx)."
  exit 1
fi

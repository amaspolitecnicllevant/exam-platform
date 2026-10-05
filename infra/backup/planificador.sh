#!/bin/sh
# Fa una còpia cada dia a l'hora BACKUP_HORA (HH:MM, hora local del contenidor).
set -u
HORA="${BACKUP_HORA:-02:30}"
echo "[planificador] còpia diària a les $HORA (retenció ${BACKUP_RETENCIO_DIES:-30} dies)"
while true; do
  ara=$(date +%s)
  objectiu=$(date -d "$(date +%Y-%m-%d) $HORA" +%s 2>/dev/null || date -D '%Y-%m-%d %H:%M' -d "$(date +%Y-%m-%d) $HORA" +%s)
  [ "$objectiu" -le "$ara" ] && objectiu=$((objectiu + 86400))
  sleep $((objectiu - ara))
  /backup/fes-copia.sh || echo "[planificador] la còpia ha fallat (vegeu estat.json)"
done

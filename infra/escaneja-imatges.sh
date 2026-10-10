#!/usr/bin/env bash
# Escaneja amb Trivy les imatges de Docker que fa servir la plataforma (les de l'aplicació, les de la BD i les del sandbox
# d'execució) i mostra les vulnerabilitats CRÍTIQUES i ALTES. No canvia res: només llegeix.
#
#   ./escaneja-imatges.sh            totes les imatges del compose i les del sandbox
#   ./escaneja-imatges.sh IMATGE…    només les indicades
#
# Les vulnerabilitats de paquets del sistema es corregeixen reconstruint amb els últims pegats:
#   docker compose build --pull --no-cache backend frontend && docker compose up -d
# (els Dockerfile fan «apk upgrade»; sense --no-cache, Docker reutilitza la capa antiga i no porta pegats nous).
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -gt 0 ]; then
  IMATGES=("$@")
else
  mapfile -t IMATGES < <(docker compose config --images 2>/dev/null | sort -u)
  IMATGES+=("bash:5" "eclipse-temurin:21-jdk-alpine")
fi

docker volume create trivy-cache >/dev/null
for img in "${IMATGES[@]}"; do
  echo "=== $img"
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache aquasec/trivy:latest \
    image --quiet --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed "$img" || true
done

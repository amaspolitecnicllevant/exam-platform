#!/usr/bin/env bash
# Prepara una VM Ubuntu Server (22.04/24.04) per executar la plataforma d'exàmens.
# Instal·la Docker Engine + Compose, crea els directoris de dades, descarrega les
# imatges del sandbox i configura el tallafocs.
#
# Ús (com a usuari normal, NO com a root; demanarà sudo):
#   ./prepara-vm.sh
#
# Després cal tancar la sessió i tornar a entrar perquè el grup "docker" tingui efecte.

set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
  echo "Executa'l com a usuari normal, no com a root." >&2
  exit 1
fi

echo "==> 1/5 Sistema base"
sudo apt update
sudo apt full-upgrade -y
sudo apt install -y qemu-guest-agent ca-certificates curl git rsync openssl libnss3-tools
# A Ubuntu el servei és "static" (s'activa sol per udev): no es pot fer "enable", i no ha de aturar el script
sudo systemctl start qemu-guest-agent || true
sudo timedatectl set-timezone Europe/Madrid

echo "==> 2/5 Docker Engine + plugin Compose (repositori oficial)"
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

echo "==> 3/5 Directoris de dades de l'aplicació"
# El backend corre dins el contenidor com a uid 100 / gid 101 (usuari «appuser»). Aquestes carpetes han de ser
# SEVES: si són de l'usuari de la VM, no s'executa cap codi d'alumne i no es pot pujar cap fitxer (AccessDeniedException).
sudo mkdir -p /opt/exam-scripts /opt/exam-files
sudo chown 100:101 /opt/exam-scripts /opt/exam-files

echo "==> 4/5 Imatges del sandbox i de la BD"
sudo docker pull bash:5
sudo docker pull eclipse-temurin:21-jdk-alpine
sudo docker pull postgres:16-alpine

echo "==> 5/5 Tallafocs (SSH, HTTP 3000, HTTPS 3443)"
sudo ufw allow 22/tcp
sudo ufw allow 3000/tcp
sudo ufw allow 3443/tcp
sudo ufw --force enable
sudo ufw status

echo
echo "Fet. Tanca la sessió i torna a entrar (grup docker). Després comprova:"
echo "  docker run --rm hello-world && docker compose version"
echo
echo "Recorda: els ports publicats per Docker se salten UFW. Revisa que el port de"
echo "PostgreSQL no estigui exposat a infra/docker-compose.yml."

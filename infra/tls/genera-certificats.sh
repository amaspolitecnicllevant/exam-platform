#!/usr/bin/env bash
# Genera els certificats per servir la plataforma per HTTPS dins la xarxa del centre (accés per IP).
#
#   ./tls/genera-certificats.sh 192.168.1.20 [altres IP o noms...]
#
# - La primera vegada crea l'autoritat de certificació del centre (tls/certs/ca.crt i ca.key, 10 anys).
#   ca.crt és el fitxer que s'instal·la als ordinadors perquè el navegador confiï en la web.
#   ca.key és la clau que signa: guarda-la bé i no la copiïs enlloc més.
# - Cada vegada (re)genera el certificat del servidor (server.crt/server.key, 825 dies) per a les
#   IP indicades, signat per l'autoritat. Per renovar-lo o si canvia la IP, torna'l a executar:
#   els ordinadors no s'han de tocar (confien en l'autoritat, no en el certificat).
set -euo pipefail
cd "$(dirname "$0")"
[[ $# -ge 1 ]] || { echo "Ús: $0 <IP del servidor> [altres IP o noms...]" >&2; exit 2; }

DIR=certs
mkdir -p "$DIR"
chmod 700 "$DIR"
umask 077

if [[ ! -f "$DIR/ca.key" ]]; then
  echo "→ Creant l'autoritat de certificació del centre…"
  openssl req -x509 -new -newkey rsa:4096 -nodes -sha256 -days 3650 \
    -keyout "$DIR/ca.key" -out "$DIR/ca.crt" \
    -subj "/O=Plataforma d'examens/CN=Autoritat del centre (plataforma d'examens)" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
  chmod 644 "$DIR/ca.crt"
else
  echo "→ Es reutilitza l'autoritat existent ($DIR/ca.crt)"
fi

SAN=""
for nom in "$@"; do
  if [[ "$nom" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ || "$nom" == *:* ]]; then SAN+="IP:$nom,"; else SAN+="DNS:$nom,"; fi
done
SAN="${SAN%,}"

echo "→ Generant el certificat del servidor per a: $*"
openssl req -new -newkey rsa:2048 -nodes -keyout "$DIR/server.key" -out "$DIR/server.csr" \
  -subj "/O=Plataforma d'examens/CN=$1" 2>/dev/null
cat > "$DIR/server.ext" <<EXT
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=$SAN
EXT
openssl x509 -req -in "$DIR/server.csr" -CA "$DIR/ca.crt" -CAkey "$DIR/ca.key" -CAcreateserial \
  -out "$DIR/server.crt" -days 825 -sha256 -extfile "$DIR/server.ext" 2>/dev/null
rm -f "$DIR/server.csr" "$DIR/server.ext"
chmod 644 "$DIR/server.crt"

openssl verify -CAfile "$DIR/ca.crt" "$DIR/server.crt" >/dev/null
echo "✓ Fet. Caduca el $(openssl x509 -in "$DIR/server.crt" -noout -enddate | cut -d= -f2)"
echo
echo "Fitxer per instal·lar als ordinadors: $(pwd)/$DIR/ca.crt"
echo "Si l'HTTPS ja està actiu: docker compose up -d --force-recreate frontend"

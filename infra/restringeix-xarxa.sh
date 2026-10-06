#!/usr/bin/env bash
# Limita l'accés a la VM (SSH i web) a les xarxes del centre.
#
#   sudo ./restringeix-xarxa.sh                 aplica la restricció (rang per defecte: 10.100.0.0/16)
#   sudo ./restringeix-xarxa.sh --rang 10.100.0.0/16
#   ./restringeix-xarxa.sh --prova              només mostra què faria (no cal sudo)
#   sudo ./restringeix-xarxa.sh --desfes        torna a obrir 22/3000/3443 a tothom
#   sudo ./restringeix-xarxa.sh --forca         salta la comprovació de la teva connexió SSH
#
# Per què calen dues peces:
#  - SSH (22): es limita amb UFW.
#  - Web (3000/3443): Docker publica aquests ports saltant-se UFW, així que es limiten amb una regla a la
#    cadena DOCKER-USER d'iptables. Es desa com a servei systemd (exam-firewall) per sobreviure reinicis
#    i reinicis de Docker.
# IPv6: no s'obre cap port web per IPv6 (en treure les regles "Anywhere" d'UFW queda tancat).
set -euo pipefail

RANG="10.100.0.0/16"
PROVA=0; DESFES=0; FORCA=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --prova) PROVA=1 ;;
    --desfes) DESFES=1 ;;
    --forca) FORCA=1 ;;
    --rang) [[ -n "${2:-}" ]] || { echo "--rang requereix un valor (p. ex. 10.100.0.0/16)" >&2; exit 2; }; RANG="$2"; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "Opció desconeguda: $1 (vegeu --help)" >&2; exit 2 ;;
  esac
  shift
done

cd "$(dirname "$0")"
HTTPS_PORT=$(grep -E '^HTTPS_PORT=' .env 2>/dev/null | tail -1 | cut -d= -f2 || true)
HTTPS_PORT=${HTTPS_PORT:-3443}
HTTP_PORT=3000
IFACE=$(ip route show default 2>/dev/null | awk '/default/ {print $5; exit}')
HELPER=/usr/local/sbin/exam-firewall-docker
UNIT=/etc/systemd/system/exam-firewall.service

dins_del_rang() { # $1 = IP, $2 = CIDR
  python3 -c 'import ipaddress,sys; sys.exit(0 if ipaddress.ip_address(sys.argv[1]) in ipaddress.ip_network(sys.argv[2], strict=False) else 1)' "$1" "$2"
}
python3 -c 'import ipaddress,sys; ipaddress.ip_network(sys.argv[1], strict=False)' "$RANG" 2>/dev/null \
  || { echo "Rang no vàlid: $RANG" >&2; exit 2; }
[[ -n "$IFACE" ]] || { echo "No s'ha pogut determinar la interfície de xarxa (ip route show default)" >&2; exit 1; }

run() { if (( PROVA )); then echo "  [prova] $*"; else "$@"; fi; }

if (( DESFES )); then
  echo "==> Desfent la restricció"
  run systemctl disable --now exam-firewall.service 2>/dev/null || true
  if (( ! PROVA )); then
    for p in "$HTTP_PORT" "$HTTPS_PORT"; do
      while iptables -D DOCKER-USER -i "$IFACE" -p tcp -m conntrack --ctorigdstport "$p" -j EXAM-WEB 2>/dev/null; do :; done
    done
    iptables -F EXAM-WEB 2>/dev/null || true
    iptables -X EXAM-WEB 2>/dev/null || true
    rm -f "$HELPER" "$UNIT"; systemctl daemon-reload
  else
    echo "  [prova] retirar les regles de DOCKER-USER i la cadena EXAM-WEB, esborrar $HELPER i $UNIT"
  fi
  run ufw allow 22/tcp
  run ufw allow "$HTTP_PORT/tcp"
  run ufw allow "$HTTPS_PORT/tcp"
  run ufw delete allow from "$RANG" to any port 22 proto tcp 2>/dev/null || true
  echo "Fet. SSH i web tornen a estar oberts a tothom."
  exit 0
fi

echo "Rang permès:        $RANG"
echo "Interfície externa: $IFACE"
echo "Ports web:          $HTTP_PORT, $HTTPS_PORT (Docker, via DOCKER-USER) i SSH 22 (UFW)"

# Salvaguarda: no tallar la connexió amb què s'executa això
if (( ! PROVA && ! FORCA )); then
  CLIENT="${SSH_CLIENT%% *}"
  [[ -n "$CLIENT" ]] || CLIENT=$(who -m 2>/dev/null | grep -oE '\(([0-9]+\.){3}[0-9]+\)' | tr -d '()' || true)
  if [[ -n "$CLIENT" ]]; then
    if dins_del_rang "$CLIENT" "$RANG"; then
      echo "Connexió SSH actual des de $CLIENT: dins del rang, correcte."
    else
      echo "ERROR: ara estàs connectat des de $CLIENT, que NO és dins de $RANG." >&2
      echo "Aplicar-ho et deixaria fora de la VM. Revisa el rang o fes servir --forca." >&2
      exit 1
    fi
  else
    echo "No s'ha pogut saber l'IP de la connexió SSH actual (consola?). Es continua."
  fi
fi

if (( ! PROVA && EUID != 0 )); then
  echo "Cal executar-ho amb sudo (o amb --prova per veure només el pla)." >&2
  exit 1
fi

echo "==> 1/3 Servei que aplica la regla a DOCKER-USER"
HELPER_CONTINGUT=$(cat <<EOF
#!/usr/bin/env bash
# Generat per restringeix-xarxa.sh: només el rang del centre pot arribar als ports web publicats per Docker.
set -eu
iptables -N EXAM-WEB 2>/dev/null || true
iptables -F EXAM-WEB
iptables -A EXAM-WEB -s $RANG -j RETURN
iptables -A EXAM-WEB -j DROP
for p in $HTTP_PORT $HTTPS_PORT; do
  iptables -C DOCKER-USER -i $IFACE -p tcp -m conntrack --ctorigdstport \$p -j EXAM-WEB 2>/dev/null \\
    || iptables -I DOCKER-USER 1 -i $IFACE -p tcp -m conntrack --ctorigdstport \$p -j EXAM-WEB
done
EOF
)
UNIT_CONTINGUT=$(cat <<EOF
[Unit]
Description=Restringeix el web de la plataforma d'exàmens a les xarxes del centre
After=docker.service network-online.target
Requires=docker.service
PartOf=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=$HELPER

[Install]
WantedBy=multi-user.target docker.service
EOF
)
if (( PROVA )); then
  echo "  [prova] escriure $HELPER:"; echo "$HELPER_CONTINGUT" | sed 's/^/      /'
  echo "  [prova] escriure $UNIT i activar-lo"
else
  printf '%s\n' "$HELPER_CONTINGUT" > "$HELPER"; chmod 755 "$HELPER"
  printf '%s\n' "$UNIT_CONTINGUT" > "$UNIT"
  systemctl daemon-reload
  systemctl enable --now exam-firewall.service
fi

echo "==> 2/3 SSH: només des de $RANG (UFW)"
# Primer s'afegeix la regla nova i després s'esborren les antigues, per no tallar mai l'accés
run ufw allow from "$RANG" to any port 22 proto tcp comment 'SSH centre'
run ufw delete allow 22/tcp 2>/dev/null || true
run ufw delete allow "$HTTP_PORT/tcp" 2>/dev/null || true
run ufw delete allow "$HTTPS_PORT/tcp" 2>/dev/null || true
run ufw reload

echo "==> 3/3 Estat"
if (( PROVA )); then
  echo "(prova: no s'ha canviat res)"
else
  ufw status
  echo; iptables -S DOCKER-USER; iptables -S EXAM-WEB
  echo
  echo "Fet. Comprova des d'un altre ordinador del centre que https://<IP o nom>:$HTTPS_PORT continua obrint-se."
  echo "Per desfer-ho: sudo $0 --desfes"
fi

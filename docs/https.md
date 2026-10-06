# HTTPS dins la xarxa del centre

Sense HTTPS, les contrasenyes i els tokens de sessió viatgen en clar per la xarxa: qualsevol ordinador de
la mateixa xarxa els podria capturar. Com que s'hi entra per IP (sense nom de domini), no es pot fer
servir un certificat públic (Let's Encrypt). Es fa servir una **autoritat de certificació pròpia del centre**:
s'instal·la un cop a cada ordinador i, a partir d'aquí, el navegador confia en la web sense avisos.

Totes les ordres s'executen des de la carpeta `infra/`.

## Estat actual

⏳ **Preparat però no activat.** La web continua per HTTP al port 3000.

## Abans de començar

- **El servidor ha de tenir una IP fixa** (reservada al DHCP o configurada a mà). El certificat es fa per
  a una IP concreta: si canvia, el navegador avisarà fins que es torni a generar.
- **Cal poder instal·lar l'autoritat als ordinadors** dels alumnes i dels professors (normalment ho fa
  qui administra els equips del centre). Fins que no hi sigui, el navegador mostrarà un avís de seguretat.
- L'HTTPS va al port **3443** (els ports 80 i 443 d'aquesta màquina ja els fa servir un altre servei).
  Es pot canviar amb `HTTPS_PORT` a `infra/.env`.

## 1. Generar els certificats

```bash
./tls/genera-certificats.sh 192.168.1.20      # la IP del servidor (se'n poden posar diverses)
```

Crea a `infra/tls/certs/`:

| Fitxer | Què és |
|---|---|
| `ca.crt` | L'autoritat del centre. **És el fitxer que s'instal·la als ordinadors.** Caduca en 10 anys. |
| `ca.key` | La clau que signa. **Guarda-la i no la copiïs enlloc**: qui la tingui podria fer certificats en què confiarien tots els ordinadors del centre. |
| `server.crt`, `server.key` | El certificat del servidor, vàlid 825 dies (uns 2 anys i 3 mesos). |

## 2. Activar l'HTTPS

A `infra/.env`:

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.https.yml
# Si també tens activada la còpia a la carpeta compartida:
# COMPOSE_FILE=docker-compose.yml:docker-compose.copies-remotes.yml:docker-compose.https.yml
```

```bash
docker compose up -d frontend
```

A partir d'aquí la web és a `https://192.168.1.20:3443`. L'adreça antiga (`http://192.168.1.20:3000`)
redirigeix sola a la nova, de manera que els enllaços i les adreces d'interès continuen funcionant.

Convé activar-ho un dia sense exàmens i quan l'autoritat ja estigui instal·lada als ordinadors.

## 3. Instal·lar l'autoritat als ordinadors

Cal el fitxer `infra/tls/certs/ca.crt` (per exemple, en un llapis USB o una carpeta compartida).

**Windows** (Chrome, Edge i Firefox recents), com a administrador:

```
certutil -addstore -f Root ca.crt
```

o bé doble clic a `ca.crt` → *Instal·la el certificat* → *Equip local* → *Col·loca tots els certificats en
el magatzem següent* → *Entitats de certificació arrel de confiança*. En un domini, es pot distribuir a
tots els equips amb una directiva de grup (GPO).

**Linux (Ubuntu, Debian, Lliurex)**, com a administrador:

```bash
sudo cp ca.crt /usr/local/share/ca-certificates/plataforma-examens.crt
sudo update-ca-certificates
```

Chrome i Chromium a Linux fan servir el seu propi magatzem. Per a cada usuari:

```bash
certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n "Plataforma d'examens" -i ca.crt   # paquet libnss3-tools
```

**Firefox** (qualsevol sistema), si continua avisant: *Configuració → Privadesa i seguretat → Certificats →
Mostra els certificats → Entitats → Importa* → `ca.crt` → marca *Confia en aquesta CA per identificar
llocs web*.

**Comprovar-ho:** obre `https://<IP>:3443`. No hi ha d'haver cap avís i el cadenat ha de sortir tancat.

## Renovar el certificat

El certificat del servidor dura 825 dies. Per renovar-lo fes servir el script, que reutilitza les IP/noms
del certificat actual, conserva l'anterior a `tls/certs/anteriors/`, recarrega nginx **sense tallar
connexions** (es pot fer amb un examen en marxa) i comprova que l'HTTPS respon amb el certificat nou:

```bash
./tls/renova-certificat.sh --prova     # només mostra quan caduquen el certificat i l'autoritat
./tls/renova-certificat.sh             # renova només si queden menys de 60 dies
./tls/renova-certificat.sh --forca     # renova ara
```

Per planificar-ho cada setmana (`crontab -e`, amb un usuari del grup `docker`):

```
0 4 * * 1  /home/USUARI/exam-platform/infra/tls/renova-certificat.sh >> /home/USUARI/renova-certificat.log 2>&1
```

El script s'atura si falta `ca.key`, perquè regenerar l'autoritat obligaria a reinstal·lar-la a tots els
ordinadors. L'autoritat dura 10 anys i el script avisa quan en queda menys d'un.

Es reutilitza l'autoritat existent, de manera que **no cal tocar els ordinadors**.

## Canviar la IP del servidor

```bash
./tls/genera-certificats.sh <IP nova>
docker compose up -d --force-recreate frontend
```

Actualitza també `FRONTEND_URL` i `APP_ALLOWED_ORIGINS` a `infra/.env`.

## Desactivar-lo

Treu `docker-compose.https.yml` de `COMPOSE_FILE` a `infra/.env` i executa `docker compose up -d frontend`.

## Notes

- No s'activa HSTS a propòsit: amb una autoritat pròpia, si algun dia es tornés a HTTP o canviés
  l'autoritat, els navegadors que l'haguessin vist no deixarien entrar.
- **Entrar amb Google** no funciona accedint per IP: Google només admet adreces de retorn amb un nom de
  domini públic (o `localhost`). Per fer-lo servir caldria un nom de domini. El botó només surt a la pàgina d'inici de sessió si Google està configurat al servidor.
- Còpia de seguretat: `infra/tls/certs/` no entra a les còpies automàtiques. Guarda'n una còpia de
  `ca.key` i `ca.crt` en un lloc segur; si es perden, caldria generar una autoritat nova i tornar-la a
  instal·lar a tots els ordinadors.

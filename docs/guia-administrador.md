# Guia de l'administrador

Aquesta guia és per a qui instal·la, configura i manté la plataforma d'exàmens (SEDEX). Explica què fa
l'aplicació, com està muntada i, sobretot, **què es pot configurar** i on: al fitxer `infra/.env`, a la pantalla
*Configuració* de l'aplicació o, per a uns pocs valors, al `docker-compose.yml`.

Altres guies: [professor](guia-professor.md) · [alumne](guia-alumne.md) · [desplegament en una VM](desplegament-vm.md) ·
[HTTPS](https.md) · [còpies de seguretat](copies-seguretat.md) · [usuaris d'examen als ordinadors](usuari-examen-linux.md) · [funcionalitats per rol](funcionalitats.md).

---

## 1. Què fa l'aplicació

Els **professors** creen exàmens (des d'un fitxer Markdown o amb l'editor de l'aplicació), els activen per a una classe i
els corregeixen. Els **alumnes** els fan des del navegador, dins d'una aula o des de casa, i el servidor guarda les respostes a
mesura que escriuen. L'**administrador** gestiona usuaris, l'estructura del centre i el sistema.

| Què | Com ho fa |
|:--|:--|
| Tipus de pregunta | test, text curt/llarg, comanda o script de bash i PowerShell, programa Java, HTML/CSS i **lliurament de fitxer** (Word, Excel, Packet Tracer…) |
| Correcció | automàtica (test), per conceptes clau (text) i executant el codi en un contenidor aïllat; sempre **proposta** que el professor revisa abans de publicar les notes |
| Control d'accés | per mòdul matriculat, per grup, per alumnes concrets i per **aula** (rang d'adreces de xarxa) |
| Anti-còpia | informe de respostes molt semblants, pèrdues de focus, nom de l'alumne a la capçalera, opcions barrejades per alumne |
| Dades | PostgreSQL; els fitxers (adjunts i lliuraments) al disc del servidor; còpia diària automàtica |
| Resultats | notes per alumne, per RA, estadístiques, exportació a CSV/Excel, i respostes anònimes per revisar amb una IA |

L'aplicació **no depèn de cap servei extern**: funciona sencera dins la xarxa del centre. El login amb Google és opcional.

---

## 2. Com està muntada

Quatre contenidors (`infra/docker-compose.yml`) i els que el backend llança per executar codi:

| Servei | Per a què | Port |
|:--|:--|:--|
| **frontend** | nginx: serveix la web i reenvia l'API al backend | 3000 (HTTP) i 3443 (HTTPS, si s'activa) |
| **backend** | Spring Boot: l'API, la correcció i l'execució de codi | només `127.0.0.1:8080` (no accessible des de la xarxa) |
| **db** | PostgreSQL 16 | només `127.0.0.1:5432` |
| **backup** | còpia diària de la BD i dels fitxers | — |
| contenidors d'execució | un per cada execució de codi d'alumne: sense xarxa, amb límits de memòria, CPU i processos | — |

**Directoris de dades al servidor**

| Ruta | Contingut |
|:--|:--|
| volum Docker `db-data` | la base de dades |
| `/opt/exam-files` | fitxers adjunts a preguntes, imatges d'enunciat i lliuraments dels alumnes (`answers/`) |
| `/opt/exam-scripts` | fitxers temporals de l'execució (es poden esborrar) |
| `BACKUP_DIR` (per defecte `../backups`) | còpies de seguretat |

**Les dues carpetes han de ser de l'usuari del backend** (uid 100, gid 101: `sudo chown 100:101 /opt/exam-files /opt/exam-scripts`), no de l'usuari de la màquina. Si no, el backend no hi pot escriure: **no s'executa cap codi d'alumne i no es pot pujar cap fitxer**, i el servidor ho amaga (respon «200» amb un error a dins de l'execució, o un error 500 en pujar). Comprova-ho després d'instal·lar: `docker compose exec backend sh -c 'touch /opt/exam-scripts/x && rm /opt/exam-scripts/x && echo OK'`.

> El backend es comunica amb Docker pel *socket* (`/var/run/docker.sock`) per llançar els contenidors d'execució. Això dóna al
> backend permisos equivalents a *root* a la màquina: no hi instal·lis res més que no sigui de confiança i no exposis el port 8080.

**Requisits de maquinari** (estimacions, no mesurades; fes una prova de càrrega abans d'un examen gran)

| | Mínim (prova o grup petit) | ~120 alumnes simultanis | Si són molts programes Java |
|:--|:--|:--|:--|
| CPU | 2 nuclis | 8 nuclis | 12–16 nuclis |
| RAM | 4 GB | 16 GB | 16–32 GB |
| Disc | 20 GB SSD | 50–100 GB SSD | 100 GB NVMe |
| Xarxa | LAN 100 Mbit | LAN 1 Gbit per cable; punts d'accés wifi dimensionats | |

La instal·lació pas a pas (VM a Proxmox, Docker, clonar el repositori) és a [desplegament-vm.md](desplegament-vm.md).

---

## 3. Primer accés: crear l'administrador

L'aplicació no té registre públic ni cap compte inicial. El primer administrador es crea directament a la base de dades, **una sola
vegada**. A `infra/`, amb els serveis en marxa:

```bash
read -rs -p "Contrasenya de l'administrador: " PW; echo
docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<SQL
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO users (name, email, password_hash, role, enabled)
VALUES ('Administrador', 'correu@centre.cat', crypt('$PW', gen_salt('bf', 10)), 'ADMIN', true);
DROP EXTENSION pgcrypto;
COMMIT;
SQL
unset PW
```

Posa el teu correu i, després d'entrar, canvia la contrasenya des del botó de la barra lateral. Un `ADMIN` pot fer tot el que fa un
professor (veu **tots** els grups i exàmens, no només els seus) i a més gestiona el sistema. Si en vols una vista neta de professor, crea
un segon compte amb el rol `PROFESSOR`.

Els altres usuaris els crees des de *Gestió d'usuaris* (un a un o important un CSV) o els alumnes es registren amb un link de convit.

---

## 4. Configuració del `.env`

### 4.1 On és i com s'aplica

- El fitxer és **`infra/.env`** (al costat del `docker-compose.yml`). La plantilla és `.env.example` a l'arrel del repositori:
  `cd infra && cp ../.env.example .env`.
- No és al repositori (conté secrets). Guarda'n una còpia en un lloc segur.
- Després de canviar-lo: **`docker compose up -d`**. Un `docker compose restart` **no** rellegeix el fitxer.
- Per comprovar què rep el backend: `docker compose exec backend env | grep -E "EXEC_|JWT|FRONTEND"`.
- **Només funcionen les variables d'aquesta secció.** El `application.yml` del backend admet més noms, però si el compose no els passa
  al contenidor, posar-los al `.env` no fa res (vegeu §4.8).

### 4.2 Obligatòries

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `DB_PASS` | *(cap, obligatòria)* | Contrasenya de PostgreSQL. Genera-la amb `openssl rand -hex 24`. **Només s'aplica en crear la base de dades**: canviar-la després al `.env` no la canvia dins PostgreSQL (caldria `ALTER USER`). |
| `JWT_SECRET` | *(cap, obligatòria)* | Clau amb què es signen les sessions. `openssl rand -base64 48`. El backend **no arrenca** si és buida, curta (< 32 bytes) o és un valor que ha estat públic. Canviar-la **tanca la sessió de tothom**. |
| `DB_NAME` | `examplatform` | Nom de la base de dades. No cal tocar-lo; canviar-lo amb la BD creada no la reanomena. |
| `DB_USER` | `examuser` | Usuari de PostgreSQL. Igual que l'anterior. |

### 4.3 Adreces i accés

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `APP_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígens des d'on es pot fer servir l'aplicació (CORS), separats per comes. Han de ser **exactament** el que escriuen els usuaris al navegador: esquema, nom o IP i port (`https://10.0.0.5:3443`). Si falta l'adreça que es fa servir, el navegador bloqueja les peticions i **el login falla**. Si s'hi entra per IP i per nom, posa-les totes dues. |
| `FRONTEND_URL` | `http://localhost:3000` | URL pública, sense barra final. **Només la fa servir el login amb Google** (a on redirigeix en acabar). Amb usuari i contrasenya no té efecte. |
| `COMPOSE_FILE` | *(només `docker-compose.yml`)* | Quins fitxers de compose s'apliquen, separats per «:». Serveix per activar l'HTTPS (`docker-compose.https.yml`) i la còpia remota (`docker-compose.copies-remotes.yml`). |
| `EQUIPS_TOKEN` | *(buit: desactivat)* | Testimoni compartit amb els ordinadors de les aules perquè enviïn l'informe del seu estat (pantalla *Aules*). Genera'l amb `openssl rand -hex 24`. Sense valor, la recepció d'informes no funciona. |
| `EQUIPS_SENSE_NOTICIES_MIN` | `45` | Minuts sense cap informe a partir dels quals un ordinador surt com a «sense notícies». Els ordinadors n'envien un cada 15. |
| `EQUIPS_AVIS_DIES` | `7` | Dies sense cap informe a partir dels quals es diu que un ordinador **fa temps que no s'encén**. Els professors ho veuen en preparar un examen amb aula i han de demanar que s'encenguin. |
| `HTTPS_PORT` | `3443` | Port de l'HTTPS. Vegeu [https.md](https.md) (certificats, instal·lació de `ca.crt` als ordinadors, renovació). |

Amb l'HTTPS actiu, el port 3000 només redirigeix al 3443.

### 4.4 Execució del codi dels alumnes

Cada resposta de codi (bash, PowerShell, Java) s'executa en un contenidor nou i aïllat: **sense xarxa**, amb límit de memòria, de CPU i de
processos. Aquestes variables decideixen quantes n'hi pot haver alhora.

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `DOCKER_GID` | `984` | Identificador del grup `docker` del servidor: executa `getent group docker` i agafa el tercer camp (`docker:x:988:` → 988). **És el més fàcil d'oblidar:** si no coincideix, el backend arrenca igualment però **cap execució de codi funciona**. El 984 és el d'una màquina concreta; al teu servidor probablement és un altre (988, 999…). |
| `EXEC_MAX_CONCURRENT` | `4` | Execucions simultànies a tota la màquina. Les que passen d'aquest nombre esperen torn. Amb molts alumnes entregant alhora, 16–24 (menys si és Java: cada execució consumeix més). |
| `EXEC_QUEUE_WAIT` | `30` | Segons que una execució pot esperar torn abans de fallar. Amb cua llarga, 60–120. |
| `EXEC_MEMORY` | `128m` | Memòria de cada contenidor d'execució. 128m és suficient per a scripts; 192m–256m si hi ha molt de Java. |
| `JAVA_IMAGE` | `eclipse-temurin:21-jdk-alpine` | Imatge en què es compila i executa Java. |

Capacitat: a l'entrega, un examen amb 10 preguntes de codi i 120 alumnes són unes 1.200 execucions. Amb un script de bash són uns
0,5–1 s cadascuna; amb Java, 2–5 s (compilar i arrencar la JVM). Són estimacions, no mesures. Si fa falta, puja `EXEC_MAX_CONCURRENT` i `EXEC_QUEUE_WAIT`, i comprova
que el servidor té CPU lliure.

Les imatges (`bash:5`, `eclipse-temurin:21-jdk-alpine`) s'han de baixar abans del primer examen perquè ningú esperi: `docker pull bash:5`.
Si fas servir preguntes de PowerShell, baixa també `mcr.microsoft.com/powershell:latest` (la primera execució la baixaria en ple examen).

### 4.5 Recursos

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `BACKEND_MEM_LIMIT` | `1536m` | Memòria màxima del backend. La JVM n'usa el 70 %. Amb molts alumnes, `3g` o més. |

### 4.6 Login amb Google (opcional)

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `GOOGLE_CLIENT_ID` | *(buit)* | Credencials d'OAuth2 de Google Cloud. Buides, **el botó «Entra amb Google» no surt**. |
| `GOOGLE_CLIENT_SECRET` | *(buit)* | |

**Google només accepta adreces de retorn amb un nom de domini públic.** Si s'hi entra per IP, aquest login no pot funcionar. Els dominis de
correu permesos (p. ex. `centre.cat`) es configuren a l'aplicació (*Configuració*), no al `.env`.

### 4.7 Còpies de seguretat

El servei `backup` fa una còpia cada dia de la base de dades i de `/opt/exam-files` i en guarda els últims dies. Detalls i com restaurar:
[copies-seguretat.md](copies-seguretat.md).

| Variable | Per defecte | Què fa |
|:--|:--|:--|
| `BACKUP_DIR` | `../backups` | On es desen (relatiu a `infra/`). Millor en un **disc diferent** o sincronitzat fora de la màquina: una còpia al mateix disc no protegeix d'una avaria del disc. **Crea el directori abans del primer `up`**: si el crea Docker, és de *root* i el servei falla amb «Permission denied». |
| `BACKUP_HORA` | `02:30` | Hora de la còpia diària. |
| `BACKUP_RETENCIO_DIES` | `30` | Quants dies es conserven. |
| `BACKUP_TZ` | `Europe/Madrid` | Zona horària de l'hora anterior. |
| `BACKUP_UID`, `BACKUP_GID` | `1000`, `1000` | Usuari i grup propietaris de les còpies (`id -u`, `id -g`). Han de poder escriure a `BACKUP_DIR`. |

**Còpia també a una carpeta compartida (SMB):** afegeix `docker-compose.copies-remotes.yml` al `COMPOSE_FILE` i omple:

| Variable | Per defecte | |
|:--|:--|:--|
| `BACKUP_REMOT_SERVIDOR` | *(obligatòria)* | **IP** (no nom) del servidor de fitxers |
| `BACKUP_REMOT_RUTA` | *(obligatòria)* | `//192.168.1.10/copies/examens` |
| `BACKUP_REMOT_USUARI` | *(obligatòria)* | |
| `BACKUP_REMOT_CONTRASENYA` | *(obligatòria)* | sense comes |
| `BACKUP_REMOT_DOMINI` | `WORKGROUP` | |
| `BACKUP_REMOT_VERSIO_SMB` | `3.0` | |

A *Configuració* es veu l'estat de l'última còpia, i si falla o fa més de 26 hores tots els administradors veuen un avís a dalt de cada pàgina.

### 4.8 Valors que el `.env` NO canvia

Aquests noms existeixen al backend, però el compose **no els passa** al contenidor, així que posar-los al `.env` no té cap efecte (la
plantilla antiga en portava alguns, i podien semblar actius).

| Nom | Valor real | Què és |
|:--|:--|:--|
| `JWT_EXPIRATION_MS` | 14.400.000 (4 h) | Durada d'una sessió d'usuari |
| `EXEC_TIMEOUT` | 15 s | Temps màxim d'una execució de codi |
| `EXEC_CPUS` | 0,5 | CPU de cada contenidor d'execució |
| `BASH_IMAGE` | `bash:5` | Imatge de bash. **No posis `alpine:latest`: no té `bash` i trencaria els scripts** |
| `PS_IMAGE` | `mcr.microsoft.com/powershell:latest` | Imatge de PowerShell |
| `EXAM_ACCESS_WINDOW` | 20 min | En un examen programat, minuts des de l'hora d'inici durant els quals l'alumne el pot començar |
| `EXAM_AUTO_CLOSE_GRACE` | 5 min | Marge després de l'hora de fi abans de tancar l'examen automàticament |
| `CORRECTION_THREADS` | 2 | Fils que executen el codi en segon pla en entregar |
| `CORRECTION_QUEUE` | 1000 | Sessions que poden esperar la correcció en segon pla |
| `APP_ZONA_HORARIA` | `Europe/Madrid` | Zona horària dels missatges (les dates es desen en UTC) |
| `TRUSTED_PROXIES` | nginx de Docker i la màquina | Quins proxies poden dir la IP real del client (és la que es compara amb l'aula) |
| `OAUTH_ALLOWED_DOMAIN` | `politecnicllevant.cat` | Domini de Google per defecte; el que compta és el de *Configuració* |
| `SCRIPTS_HOST_PATH`, `FILES_HOST_PATH` | `/opt/exam-scripts`, `/opt/exam-files` | Fixos al compose |

**Per canviar-ne un** (excepte els dos últims, que són fixos): afegeix-lo a `infra/docker-compose.yml`, dins `backend` → `environment`, i
aplica'l amb `docker compose up -d backend`:

```yaml
      EXEC_TIMEOUT: ${EXEC_TIMEOUT:-15}
```

També hi ha límits fixos al codi: fitxers de lliurament de **10 MB** (al backend, a l'nginx de 12 MB i a la pantalla de l'alumne: canviar-lo
vol dir tocar els tres llocs), imatges d'enunciat de 5 MB, i 128 processos per contenidor d'execució.

### 4.9 Exemple de `.env` per a ~120 alumnes

```bash
DB_PASS=<openssl rand -hex 24>
JWT_SECRET=<openssl rand -base64 48>
APP_ALLOWED_ORIGINS=https://examens.centre.cat:3443,https://10.0.0.5:3443
FRONTEND_URL=https://examens.centre.cat:3443
COMPOSE_FILE=docker-compose.yml:docker-compose.https.yml
DOCKER_GID=988
EXEC_MAX_CONCURRENT=16
EXEC_QUEUE_WAIT=90
EXEC_MEMORY=128m
BACKEND_MEM_LIMIT=3g
BACKUP_DIR=/mnt/copies/examens
BACKUP_UID=1000
BACKUP_GID=1000
```

---

## 5. Què es configura des de l'aplicació

*Configuració* (menú d'administrador) guarda aquests valors a la base de dades; es canvien sense reiniciar res:

| Opció | Per defecte | Efecte |
|:--|:--|:--|
| Nom del centre, logo i color | SEDEX | Imatge de l'aplicació |
| Curs actiu | `2026-27` | Curs per defecte en matricular |
| Durada per defecte | 90 min | Durada en crear un examen |
| Penalització per defecte | 0 | Resta per resposta errada en un test |
| Llindar de pèrdues de focus | 5 | A partir d'aquí el monitor avisa el professor |
| Període de gràcia | 0 s | Temps extra en acabar el temps |
| Dominis de correu (Google) | `politecnicllevant.cat` | Quins correus poden entrar amb Google |
| Llindar de còpies | 80 % | Semblança a partir de la qual l'informe marca dues respostes |
| Llindar de còpies amb apunts | 95 % | El mateix per a preguntes on es poden fer servir apunts |
| Permet pujar fitxers | sí | Si es desactiva, cap alumne pot pujar fitxers (els ja pujats es conserven) |

---

## 6. Gestió del centre

- **Estructura acadèmica:** departaments, cicles i mòduls, i quin professor imparteix quin mòdul cada curs. La matrícula és **per mòdul**: un
  alumne pot estar en mòduls de cicles diferents.
- **Usuaris:** crear-los, eliminar-los (no si tenen exàmens fets o creats) i **importar-los des d'un CSV** (nom, correu i, opcionalment,
  contrasenya, mòdul, curs i grup; si no hi ha contrasenya se'n genera una, que es pot descarregar **una sola vegada**).
- **Matrícules:** per mòdul i curs, moltes alhora.
- **Aules:** nom i rang d'adreces en format CIDR (`10.0.1.0/24`). Un examen amb aula només es pot fer des d'aquestes màquines. La comprovació
  la fa el servidor amb la IP real del client.
- **Registre d'activitat:** qui ha creat, publicat, tancat o exportat què, i els reinicis de sessions i canvis de contrasenya.
- **Espai ocupat:** quant ocupen els fitxers dels exàmens **per professor, departament, cicle, mòdul i examen**, l'espai lliure del
  disc i un avís si la base de dades i el disc no coincideixen (fitxers orfes o desapareguts).

---

## 7. Manteniment

| Tasca | Com |
|:--|:--|
| Veure l'estat | `docker compose ps` |
| Veure què passa | `docker compose logs -f backend` |
| **Actualitzar** | `git pull`, `docker compose build backend frontend`, `docker compose up -d backend frontend`. **Abans, comprova que no hi ha cap examen en marxa** (el backend es reinicia uns 20 s). Si hi ha migracions de BD, fes una còpia abans. |
| Còpia ara | `infra/backup/fes-copia.sh` |
| Comprovar una còpia | `infra/backup/restaura.sh --prova` (no toca res) |
| Renovar el certificat HTTPS | `infra/tls/renova-certificat.sh` (el certificat dura 825 dies; no cal tocar els ordinadors) |
| Restringir l'accés a la xarxa del centre | `sudo infra/restringeix-xarxa.sh` (Docker se salta el tallafocs UFW: aquest script ho resol) |

**Seguretat bàsica**

- Només es pot entrar des de la xarxa del centre si el servidor té una IP privada i el router no reenvia ports cap a ell. Comprova-ho amb qui administra la xarxa.
- Guarda `infra/tls/certs/ca.key` **fora del servidor** i no la copiïs enlloc més; qui la tingui pot fer certificats en què confiarien tots els ordinadors.
- Un professor només pot gestionar (i veure'n els resultats) els exàmens que ha creat o que són d'un mòdul que imparteix (i els administradors, tots), i veu les matrícules dels mòduls que imparteix; **la llista d'usuaris, en canvi, la veuen tots els professors**. L'autorització es comprova sempre al servidor, mai al navegador.

---

## 8. Problemes habituals

| Símptoma | Causa probable |
|:--|:--|
| El backend no arrenca | `JWT_SECRET` o `DB_PASS` buits o curts; mira `docker compose logs backend` |
| No es pot entrar (el login «no fa res») | L'adreça que escriu l'usuari no és a `APP_ALLOWED_ORIGINS` |
| Cap codi s'executa | Les carpetes `/opt/exam-*` no són escrivibles pel backend (la causa més habitual; vegeu §2), `DOCKER_GID` incorrecte, o falten les imatges (`docker pull bash:5`) |
| Les execucions fallen en entregar molts alhora | `EXEC_MAX_CONCURRENT` baix o `EXEC_QUEUE_WAIT` curt |
| Els scripts de bash fallen | `BASH_IMAGE` apuntant a una imatge sense bash (o l'imatge no baixada) |
| El navegador avisa del certificat | Falta instal·lar `ca.crt` a l'ordinador ([https.md](https.md)) |
| `Permission denied` al servei de còpies | `BACKUP_DIR` creat per Docker (és de *root*); canvia'n el propietari a `BACKUP_UID` i recrea el servei |
| Un alumne rep un error de «aula» | La seva IP no és al rang de l'aula de l'examen |
| El bloqueig de la segona pestanya no funciona | Només funciona amb HTTPS o `localhost` (necessita un context segur del navegador). Accedint per `http://IP` el navegador no ho permet i només queda el registre de pèrdues de focus. Activa l'[HTTPS](https.md) |
| Un canvi al `.env` no té efecte | Has fet `restart` en lloc de `up -d`, o la variable és de la §4.8 |
| Pujar un fitxer dona un error 500 | Les carpetes `/opt/exam-*` no són escrivibles pel backend: vegeu §2 |
| Els alumnes tenen altres programes oberts durant l'examen | La plataforma no ho pot veure ni impedir. Cal que facin l'examen amb un usuari d'examen tancat als ordinadors de l'aula: vegeu §9 |
| Un lliurament «ja no està disponible» | El fitxer ha desaparegut del disc; restaura `/opt/exam-files` d'una còpia (mira *Espai ocupat*) |

---

## 9. Els ordinadors de l'aula: usuaris d'examen

La plataforma només pot vigilar el que passa **dins del navegador**: detecta quan l'alumne canvia de pestanya o de finestra i bloqueja una
segona pestanya, però **no pot veure ni impedir** que tingui altres programes oberts (un navegador no ho permet). Perquè l'examen es faci
sense altres programes, cal preparar els **ordinadors** de l'aula. Per això, **a cada ordinador s'ha de crear una sèrie d'usuaris
per als exàmens**, que els alumnes fan servir en lloc del seu usuari habitual. La contrasenya d'aquests usuaris la saben els alumnes, de
manera que la seguretat **no pot dependre que sigui secreta**: l'usuari ha d'estar tancat de manera que no hi puguin fer res.

| Usuari (suggerit) | Perfil | Per a quins exàmens | Què pot fer |
|:--|:--|:--|:--|
| `examen` | **quiosc** | Sense lliurament de fitxer | Només el navegador, a pantalla completa, amb la plataforma. Sense escriptori, menús, terminal ni altres programes. Tancar el navegador tanca la sessió |
| `examen-fitxers` | **escriptori** | Amb lliurament de fitxer (Word, Packet Tracer…) | L'escriptori i els programes de l'ordinador, però sense permisos |

Tots dos usuaris:

- tenen la **casa a la memòria**, que es buida a cada sessió: res del que s'hi gravi (documents, descàrregues…) es conserva;
- **no tenen shell** (no s'hi pot entrar per consola ni per SSH), ni grups, ni `sudo`, ni `cron`;
- **no poden muntar un pendrive** ni fer cap acció privilegiada (regla de polkit);
- en el perfil quiosc, no poden canviar a una consola (Ctrl+Alt+F1…).

El perfil quiosc pot tenir un **menú d'entrada** (opció `--menu` / `--isard-url` de l'script) amb *Plataforma d'exàmens*, *Isard* (web) i
*Aturar l'ordinador*. Si s'activa, aquest usuari té l'única excepció de poder **apagar** l'ordinador. El menú surt una vegada per sessió:
en tancar el navegador la sessió s'acaba i es buida la casa.

La **sortida a internet** durant l'examen no la gestiona aquesta aplicació: es fa amb el programa de l'institut que limita la connexió.

**Com es fa.** A cada ordinador (Linux Mint o Ubuntu amb LightDM), amb un usuari administrador, es fa servir l'script `prepara-usuari-examen.sh`,
que és a la guia [Usuaris d'examen als ordinadors de l'aula](usuari-examen-linux.md) (apartat 6). En resum:

1. Es copia el `ca.crt` del centre a l'ordinador (el de `infra/tls/certs/`; vegeu [https.md](https.md)).
2. Es crea l'script amb el contingut de la guia i s'executa un cop per usuari:
   `sudo ./prepara-usuari-examen.sh --url https://<adreça de la plataforma> --ca ~/ca.crt` (quiosc) i
   `sudo ./prepara-usuari-examen.sh --perfil escriptori --usuari examen-fitxers` (escriptori).
3. **Es reinicia l'ordinador** (`sudo reboot`): LightDM només llegeix la configuració en arrencar; sense reiniciar, el mode quiosc no s'activa.
   Els usuaris d'examen **no surten a la llista** de la pantalla d'entrada: els alumnes han de triar l'opció d'escriure l'usuari i posar-hi el nom (`examen`).
4. **Es prova en UN sol ordinador** amb la llista de comprovació de la guia (apartat 4), i només després es repeteix a la resta.

> **Atenció.** L'script **no s'ha provat en cap ordinador real**. Pot dependre de la versió de LightDM, del gestor de finestres i del
> navegador. Fes la prova en un ordinador abans d'estendre'l, i recorda que el Firefox ha de ser el `.deb` (no el `snap`).

**Estat dels ordinadors a l'aplicació.** La pantalla *Aules* pot mostrar si els ordinadors de cada aula estan preparats: cada ordinador envia cada 15 minuts un
informe d'estat (no cal que l'aplicació s'hi connecti ni guardi credencials). Els professors, en preparar un examen amb aula, veuen quins ordinadors **fa temps
que no s'encenen** perquè demanin que els encenguin i es puguin comprovar. Es configura amb `EQUIPS_TOKEN` (§4.3) i amb una opció de l'script dels usuaris
d'examen; vegeu l'apartat 5c d'aquella guia.

**Alumnes amb permisos d'administrador.** Si alguns alumnes (per exemple, els de superior) són administradors dels ordinadors, poden desfer
aquesta configuració abans de l'examen. Cal **reclonar** els ordinadors des de la imatge abans de cada examen, i/o **comprovar-ne la integritat**
des d'un ordinador d'administració, comparant-los amb un ordinador de referència net (vegeu l'apartat 5b de la guia dels usuaris d'examen).
Aquesta comprovació detecta descuits i manipulacions normals, però no a algú amb `root` que sàpiga falsejar-la.

**Què no cobreix.** No protegeix de l'accés físic a l'ordinador (arrencar des d'un USB, canviar la BIOS: cal contrasenya de la BIOS i
arrencada només des del disc) ni del que l'alumne tingui fora de l'ordinador (el mòbil). Cap mesura tècnica ho substitueix: la vigilància
a l'aula continua sent necessària.

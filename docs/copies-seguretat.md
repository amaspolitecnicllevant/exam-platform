# Còpies de seguretat

Guia per al servidor on corre la plataforma. Totes les ordres s'executen des de la carpeta `infra/`.

## Estat actual

- ✅ **Còpia local diària** a `exam-platform/backups/` (BD + fitxers de dades de les preguntes), 30 dies de retenció.
- ⏳ **Còpia a una carpeta compartida de la xarxa: pendent de configurar** (vegeu [Activar la carpeta compartida](#activar-la-carpeta-compartida)).
  Fins que no s'activi, les còpies només són en aquest servidor: una avaria del disc les perdria juntament amb les dades.

L'estat de l'última còpia es veu a **Configuració → Còpies de seguretat**. Si una còpia falla o fa més de 26 hores
que no se'n fa cap, tots els administradors veuen una barra vermella a dalt de cada pàgina.

## Com funciona

El servei `backup` del `docker-compose.yml` fa cada dia a les 02:30:

1. `pg_dump` comprimit de la base de dades → `backups/bd/examplatform-AAAAMMDD-HHMMSS.dump`
   (es comprova que es pot llegir abans de donar-lo per bo).
2. `.tar.gz` dels fitxers de dades de les preguntes (`/opt/exam-files`) → `backups/fitxers/exam-files-….tar.gz`.
3. Una suma SHA-256 de cada fitxer (`….sha256`).
4. Esborra les còpies de més de 30 dies.
5. Si està activada, ho replica a la carpeta compartida i hi torna a verificar les sumes.

Els fitxers contenen dades dels alumnes: només els pot llegir l'usuari propietari (permisos 600).

Paràmetres a `infra/.env` (valors per defecte entre parèntesis):

| Variable | Significat |
|---|---|
| `BACKUP_DIR` (`../backups`) | Carpeta de les còpies locals |
| `BACKUP_HORA` (`02:30`) | Hora de la còpia diària |
| `BACKUP_RETENCIO_DIES` (`30`) | Dies que es conserven |
| `BACKUP_UID` / `BACKUP_GID` (`1000`) | Usuari i grup propietaris de les còpies (`id -u`, `id -g`) |

## Ordres habituals

```bash
# Fer una còpia ara mateix
docker compose exec backup /backup/fes-copia.sh

# Veure el registre del servei de còpies
docker logs infra-backup-1

# Comprovar que una còpia es pot restaurar (NO toca res de producció):
# la restaura en una BD temporal i compara el nombre de files de cada taula amb la BD real
./backup/restaura.sh --prova ../backups/bd/examplatform-AAAAMMDD-HHMMSS.dump
```

Convé fer una prova de restauració de tant en tant (per exemple, cada trimestre).

## Restaurar una còpia

```bash
./backup/restaura.sh ../backups/bd/examplatform-AAAAMMDD-HHMMSS.dump ../backups/fitxers/exam-files-AAAAMMDD-HHMMSS.tar.gz
```

- Demana confirmació (cal escriure `RESTAURA`).
- Abans de res fa una còpia de l'estat actual, per poder-ho desfer.
- Atura el frontend i el backend, substitueix la BD (i els fitxers de dades, si s'indica el `.tar.gz`) i els torna a engegar.
- Fes-ho quan no hi hagi cap examen en curs.

Per restaurar des de la carpeta compartida, copia primer els fitxers (`.dump`, `.tar.gz` i els seus `.sha256`) a
`backups/bd/` i `backups/fitxers/`.

## Activar la carpeta compartida

La plataforma copia les còpies a una carpeta compartida de Windows/Samba (protocol SMB). Docker munta la carpeta
ell mateix: no cal configurar res al sistema operatiu.

### 1. Dades que cal tenir

| Dada | Exemple | Notes |
|---|---|---|
| IP del servidor de fitxers | `192.168.1.10` | Ha de ser la IP, no el nom |
| Ruta de la carpeta | `\\192.168.1.10\copies\examens` | S'escriu amb barres normals: `//192.168.1.10/copies/examens` |
| Usuari amb permís d'escriptura | `copies-examens` | Millor un usuari només per a això |
| Contrasenya | | Sense comes |
| Domini | `WORKGROUP` | El del centre, si n'hi ha |

### 2. Preparar la carpeta

Dins de la carpeta compartida, crea un **fitxer buit** anomenat exactament:

```
.carpeta-copies-examens
```

Serveix per saber que la carpeta està ben muntada. Si no hi és, la còpia remota es marca com a error i no s'hi escriu
res (per no omplir sense adonar-nos-en un directori local buit).

### 3. Configurar `infra/.env`

Afegeix aquestes línies (amb les vostres dades):

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.copies-remotes.yml
BACKUP_REMOT_SERVIDOR=192.168.1.10
BACKUP_REMOT_RUTA=//192.168.1.10/copies/examens
BACKUP_REMOT_USUARI=copies-examens
BACKUP_REMOT_CONTRASENYA=la-contrasenya
BACKUP_REMOT_DOMINI=WORKGROUP
# Només si el servidor és antic i no accepta SMB 3.0: 2.1 o 2.0
# BACKUP_REMOT_VERSIO_SMB=3.0
```

`infra/.env` ha de tenir permisos restringits (`chmod 600 infra/.env`), perquè conté contrasenyes.

### 4. Aplicar-ho i comprovar-ho

```bash
cd infra
docker compose up -d backup                       # recrea el servei de còpies amb la carpeta muntada
docker compose exec backup ls -la /backups-remot  # hi ha de sortir .carpeta-copies-examens
docker compose exec backup /backup/fes-copia.sh   # ha d'acabar amb "Còpia replicada i verificada…"
```

A **Configuració → Còpies de seguretat** ha de sortir "Carpeta compartida: ✓ Còpia replicada i verificada a la
carpeta compartida", i a la carpeta hi ha d'haver les subcarpetes `bd/` i `fitxers/`.

### Si no funciona

| Símptoma | Causa probable |
|---|---|
| `docker compose up -d backup` falla amb `mount error(13): Permission denied` | Usuari, contrasenya o domini incorrectes, o l'usuari no té permís a la carpeta |
| `mount error(2): No such file or directory` | La ruta (`BACKUP_REMOT_RUTA`) no existeix al servidor |
| `mount error(95): Operation not supported` | Versió SMB no acceptada: prova `BACKUP_REMOT_VERSIO_SMB=2.1` |
| `mount error(113)` o temps d'espera | La IP no és accessible des d'aquest servidor (xarxa o tallafocs) |
| "La carpeta compartida no està muntada (falta .carpeta-copies-examens)" | Falta el fitxer marcador del pas 2 |
| "No s'ha pogut copiar o verificar la còpia…" | Carpeta plena o sense permís d'escriptura |

Si canvies alguna dada després d'haver-la muntat, cal esborrar el volum perquè Docker el torni a crear:

```bash
docker compose stop backup && docker compose rm -f backup && docker volume rm infra_copies-remot
docker compose up -d backup
```

### Desactivar-la

Treu la línia `COMPOSE_FILE=…` de `infra/.env` i executa `docker compose up -d backup`.

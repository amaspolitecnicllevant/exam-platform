# Desplegament en una VM (Proxmox) amb git

Guia pas a pas per posar la plataforma en producció en una VM nova. El codi es porta des de GitHub
(`git clone`) i les actualitzacions es fan amb `git pull`.

Les ordres s'executen a la **VM** tret que es digui «al teu ordinador». Valors entre `MAJÚSCULES`
(`IP_VM`, `USUARI`) són els teus.

## 0. Per què una VM

Proxmox VE 8.4 no executa Docker de manera útil per a aquesta aplicació: el backend necessita Docker per
llançar un contenidor nou per cada execució de codi dels alumnes. El camí és una **VM amb Ubuntu Server i
Docker dins**. No s'instal·la Docker al host de Proxmox. Una VM, a més, aïlla millor el codi dels alumnes
que un LXC.

## 1. Crear la VM a Proxmox

| | Valor (uns 100–120 alumnes simultanis) |
|---|---|
| Sistema | Ubuntu Server LTS (22.04 o 24.04) |
| CPU | 8 nuclis, tipus **host** |
| RAM | 16 GB, *Ballooning* desactivat |
| Disc | 100 GB, VirtIO SCSI single, en SSD/NVMe, *Discard* i *SSD emulation* |
| Xarxa | VirtIO, bridge `vmbr0`, **IP fixa** (reserva al DHCP o configurada a mà) |
| Opcions | *Qemu Agent* activat, *Start at boot* = Sí |

Aquestes xifres són estimacions, no mesures. Per a un grup petit, 4 nuclis i 8 GB són suficients.
Durant la instal·lació d'Ubuntu, marca **Install OpenSSH server**.

## 2. Primer accés per SSH

A la consola de Proxmox, mira la IP de la VM:

```bash
ip -4 a
```

Al teu ordinador, copia-hi la clau pública i entra:

```bash
ssh-copy-id USUARI@IP_VM
ssh USUARI@IP_VM
```

Si no tens clau (`ls ~/.ssh/*.pub` no mostra res): `ssh-keygen -t ed25519`.

## 3. Preparar el sistema

Al teu ordinador, copia el script a la VM:

```bash
scp infra/prepara-vm.sh USUARI@IP_VM:~
```

A la VM, executa'l com a usuari normal (no root):

```bash
./prepara-vm.sh
```

Instal·la Docker Engine + plugin Compose, crea `/opt/exam-scripts` i `/opt/exam-files`, descarrega les
imatges del sandbox (`bash:5`, `eclipse-temurin:21-jdk-alpine`, `postgres:16-alpine`) i configura UFW
(ports 22, 3000 i 3443). Quan acabi, **tanca la sessió i torna a entrar** perquè el grup `docker`
tingui efecte. Comprova:

```bash
docker run --rm hello-world
docker compose version
```

Els ports que Docker publica amb `ports:` se salten UFW. Revisa `infra/docker-compose.yml` i assegura't que
el port de PostgreSQL no queda exposat a la xarxa.

## 4. Clonar el repositori

El repositori és privat. La VM necessita la seva **pròpia** clau: una mateixa clau no es pot reutilitzar com a
*deploy key* en un altre lloc on ja estigui afegida.

```bash
ssh-keygen -t ed25519 -C "vm-examens"
cat ~/.ssh/id_ed25519.pub
```

A GitHub: *Repositori `exam-platform` → Settings → Deploy keys → Add deploy key*. Enganxa la clau, deixa
**Allow write access desmarcat** (la VM només ha de llegir) i desa. Després:

```bash
ssh -T git@github.com            # ha de dir "Hi amaspolitecnicllevant/exam-platform!"
git clone git@github.com:amaspolitecnicllevant/exam-platform.git
cd exam-platform/infra
```

## 5. Configurar `infra/.env`

`.env` no és al repositori (conté secrets). Es crea a la VM:

```bash
cp ../.env.example .env
nano .env
```

Genera els secrets:

```bash
openssl rand -base64 24     # DB_PASS
openssl rand -base64 48     # JWT_SECRET (el backend no arrenca si és buit o curt)
id -u; id -g                # BACKUP_UID i BACKUP_GID
```

Valors a omplir o revisar:

| Variable | Valor |
|---|---|
| `DB_PASS`, `JWT_SECRET` | els generats |
| `FRONTEND_URL`, `APP_ALLOWED_ORIGINS` | `http://IP_VM:3000` (o `https://IP_VM:3443` amb HTTPS), sense barra final |
| `BACKUP_UID`, `BACKUP_GID` | resultat d'`id -u` i `id -g` |
| `BACKUP_DIR` | millor un disc diferent o una carpeta sincronitzada fora de la VM |
| `EXEC_MAX_CONCURRENT` | 16 (fins a 24 amb molts alumnes de Java) |
| `EXEC_QUEUE_WAIT` | 60–120 |
| `EXEC_MEMORY` | 64m per a scripts, 192–256m per a Java |
| `BACKEND_MEM_LIMIT` | 3g o més amb molts alumnes |

L'accés és per IP dins la xarxa del centre; **Google OAuth no funciona per IP**, així que deixa
`GOOGLE_CLIENT_*` buit.

## 6. Construir i arrencar

```bash
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f backend
```

Flyway aplica les migracions de la BD automàticament. Des d'un altre ordinador de la xarxa, obre
`http://IP_VM:3000`.

## 7. Dades existents (opcional)

La BD de la VM comença buida. Per migrar alumnes i exàmens d'una altra instància, copia-hi la carpeta
`backups/` (no és al repositori) i restaura:

```bash
./backup/restaura.sh --prova      # comprova la còpia sense tocar res
./backup/restaura.sh              # restaura de debò
```

Fes-ho abans de posar la VM en producció.

## 8. HTTPS

Sense HTTPS, les contrasenyes i els tokens viatgen en clar per la xarxa. Segueix `docs/https.md`:
`./tls/genera-certificats.sh IP_VM`, afegeix `docker-compose.https.yml` a `COMPOSE_FILE` i instal·la
`ca.crt` als ordinadors d'alumnes i professors. Guarda `ca.key` a part: qui la tingui pot fer certificats
en què confiarien tots els ordinadors del centre.

## 9. Còpies de seguretat

El servei `backup` fa una còpia diària a les 02:30 (BD + fitxers d'exàmens) i en conserva 30 dies. Comprova:

- que `BACKUP_DIR` és a un lloc segur (millor fora de la VM);
- que la restauració de prova funciona (`./backup/restaura.sh --prova`);
- opcionalment, la còpia a una carpeta compartida SMB (`docs/copies-seguretat.md`);
- a Proxmox, una còpia programada de la VM sencera (*Datacenter → Backup*).

## 10. Comprovacions abans de posar-ho en producció

1. Un examen complet de cada tipus (`BASH_CMD`, `BASH_SCRIPT`, `JAVA_PROG`, `HTML_CSS`) amb un alumne de prova.
2. **Prova de càrrega** amb el nombre real d'alumnes simultanis, sobretot entregant Java alhora.
3. Des d'un ordinador d'alumne: connexió, cap avís de certificat i desat automàtic.
4. Reinicia la VM i comprova que tot arrenca sol (`docker compose ps`). Si no ho fa, afegeix
   `restart: unless-stopped` als serveis.

## 11. Actualitzar l'aplicació

Al teu ordinador, fes commit i `git push`. A la VM:

```bash
cd ~/exam-platform
git pull
cd infra
docker compose build
docker compose up -d
docker compose ps
```

Abans de redesplegar, comprova que **no hi ha sessions `IN_PROGRESS`**: reiniciar el backend en mig d'un
examen pot afectar els alumnes. Fes servir `up -d` (no `restart`) per aplicar canvis de volums o
variables. Convé fer una instantània de la VM a Proxmox abans de cada actualització gran.

## 12. Si alguna cosa falla

| Símptoma | On mirar |
|---|---|
| El backend no arrenca | `docker compose logs backend`: sovint `JWT_SECRET` buit/curt o `DB_PASS` no definit |
| Error de permisos a `/opt/exam-*` | `sudo chown -R $USER:$USER /opt/exam-scripts /opt/exam-files`; si continua, depèn de l'usuari del contenidor |
| `Permission denied (publickey)` al `git clone` | La *deploy key* no és afegida al repositori, o és de l'ordinador (ja usada) |
| El frontend no carrega des d'un altre equip | UFW (`sudo ufw status`) i `FRONTEND_URL`/`APP_ALLOWED_ORIGINS` |
| Les execucions de codi fallen o triguen | `EXEC_MAX_CONCURRENT`, `EXEC_QUEUE_WAIT`, i que les imatges del sandbox siguin descarregades |
| Cua d'entregues llarga | Pujar `EXEC_MAX_CONCURRENT`; escalonar l'hora de fi per grups |

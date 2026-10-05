# Desplegament en una VM (Proxmox)

Proxmox VE 8.4 no executa Docker de manera útil per a aquesta aplicació (el backend necessita
Docker per llançar els contenidors del sandbox). El camí és una **VM amb Ubuntu Server i Docker dins**.
No s'instal·la Docker al host de Proxmox.

## 1. Crear la VM

| | Valor (100–120 alumnes simultanis) |
|---|---|
| Sistema | Ubuntu Server LTS |
| CPU | 8 nuclis, tipus **host** |
| RAM | 16 GB, *Ballooning* desactivat |
| Disc | 100 GB, VirtIO SCSI single, SSD/NVMe, *Discard* i *SSD emulation* |
| Xarxa | VirtIO, bridge `vmbr0`, **IP fixa** |
| Opcions | *Qemu Agent* activat, *Start at boot* = Sí |

Aquestes xifres són estimacions, no mesures. Fes una prova de càrrega abans d'un examen real.

## 2. Preparar la VM

Copia i executa `infra/prepara-vm.sh` com a usuari normal (no root). Instal·la Docker Engine + Compose,
crea `/opt/exam-scripts` i `/opt/exam-files`, descarrega les imatges del sandbox i configura UFW.
Després tanca la sessió i torna a entrar (grup `docker`).

Els ports publicats per Docker se salten UFW: comprova que el de PostgreSQL no quedi exposat.

## 3. Portar el codi

A la VM, genera una clau (`ssh-keygen -t ed25519`) i afegeix-la com a *deploy key* de només lectura
del repositori. Després:

```bash
git clone git@github.com:amaspolitecnicllevant/exam-platform.git
```

`infra/.env`, `backups/` i `infra/tls/certs/` no són al repositori: s'han de crear o copiar a mà.

## 4. Configurar i arrencar

```bash
cd exam-platform/infra
cp ../.env.example .env      # omple DB_PASS, JWT_SECRET, URLs, BACKUP_UID/GID, EXEC_*
docker compose build
docker compose up -d
docker compose ps
```

Ajustos recomanats per a molts alumnes: `EXEC_MAX_CONCURRENT=16`, `EXEC_QUEUE_WAIT=60–120`,
`BACKEND_MEM_LIMIT=3g` o més, i pujar el pool de connexions de Postgres.

## 5. Abans de posar-ho en producció

1. HTTPS: vegeu `docs/https.md`.
2. Còpies de seguretat: vegeu `docs/copies-seguretat.md`; prova `restaura.sh --prova`.
3. Un examen complet de cada tipus amb un alumne de prova.
4. Prova de càrrega amb el nombre real d'alumnes.
5. Reinicia la VM i comprova que tot arrenca sol.

## 6. En producció

- Abans de redesplegar, comprova que no hi ha sessions `IN_PROGRESS`.
- Aplica canvis amb `docker compose up -d`, no amb `restart`.
- Fes una instantània de la VM abans de cada actualització gran.

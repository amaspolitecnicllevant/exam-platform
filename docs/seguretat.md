# Seguretat: auditoria i estat

Resum de l'auditoria de seguretat del 9 d'octubre de 2026 i de com s'ha tancat cada troballa. No és una prova d'intrusió: és una revisió del
codi, la configuració, la infraestructura i els scripts. L'autorització dels serveis s'ha revisat per mostreig, i no s'han pogut escanejar les
dependències de Maven ni les imatges Docker.

**Llegenda:** ✅ fet al codi o a la documentació · 🔧 requereix una acció a la VM (vegeu «Pendent a la VM») · 📝 risc acceptat o recomanació.

| # | Gravetat | Troballa | Estat |
|--:|:--|:--|:--|
| 1 | Alta | La VM és accessible des de qualsevol xarxa (Docker publica 3000/3443 saltant-se UFW) | 🔧 `infra/restringeix-xarxa.sh` ja existeix i cal executar-lo; `prepara-vm.sh` accepta `XARXA_PERMESA` per a instal·lacions noves |
| 2 | Alta | El backend té el socket de Docker (equival a `root` a la VM si algú aconseguís executar codi al backend) | 📝 Vegeu «Socket de Docker» |
| 3 | Mitjana | Tokens renovables per sempre, sense revocació, i el filtre ignorava l'usuari inhabilitat | ✅ Vida màxima de la sessió (12 h), versió de token per usuari, usuari habilitat; botó *Tancar sessions* |
| 4 | Mitjana | Permisos massa oberts a la VM (`.env`, còpies de la BD, lliuraments dels alumnes) | ✅ codi i guies · 🔧 s'ha d'aplicar a la VM actual |
| 5 | Mitjana | La configuració pública mostrava els llindars antitrampes (còpies 80 %, pèrdues de focus) | ✅ Només la veu el professorat i l'administració |
| 6 | Mitjana | Un alumne podia bloquejar el compte d'un professor amb 5 intents fallits | ✅ El límit és per compte **i adreça**; hi ha un límit més alt per compte (atac repartit) |
| 7 | Mitjana | CSP amb `script-src 'unsafe-inline'` | ✅ `script-src 'self'`; les respostes HTML dels alumnes es mostren sense executar scripts |
| 8 | Mitjana | `react-router` amb 2 vulnerabilitats moderades | 📝 No explotables aquí (no es navega mai amb una URL de l'usuari ni hi ha SSR); s'actualitzarà amb la propera revisió de dependències |
| 9 | Baixa | Fitxers de preguntes servits amb el tipus que va indicar qui els va pujar | ✅ Només es respecten les imatges de mapa de bits; la resta, descàrrega genèrica amb `nosniff` |
| 10 | Baixa | Cases de Mint llegibles per tothom (755) | ✅ L'script `--tot` les tanca (`o-rwx`) |
| 11 | Baixa | La regla de polkit d'apagar també permetia ignorar bloquejos | ✅ Només `power-off` i `power-off-multiple-sessions` |
| 12 | Baixa | Qualsevol professor veia IP, noms i empremtes dels ordinadors de qualsevol aula | ✅ Només l'aula on té un examen i sense detalls tècnics |
| 13 | Baixa | Contrasenyes i testimoni a la línia d'ordres del bucle de l'aula | ✅ Per l'entrada estàndard de SSH |
| 14 | Baixa | Contrasenyes de només 8 caràcters per a tothom | ✅ 12 per al professorat i l'administració (8 per als alumnes) |

## Què s'ha fet exactament

- **Sessions (3).** El token té una versió per usuari (`users.token_version`, migració V39). Canviar o reinicialitzar la contrasenya la puja i tanca
  totes les altres sessions; qui canvia la seva contrasenya rep un token nou. Un administrador pot tancar les sessions d'un usuari amb *Tancar sessions*
  (`POST /api/users/{id}/revoca-sessions`, queda a l'auditoria com a `SESSIONS_REVOCADES`). El token es pot renovar, però **no més enllà de
  `JWT_MAX_SESSION_MS`** (12 h) des del login. El filtre també rebutja els tokens d'un usuari inhabilitat. Els tokens emesos abans d'aquest canvi continuen
  vàlids fins que caduquin (tenen versió 0, que és la dels usuaris existents).
- **Configuració pública (5).** `GET /api/configuracio` segueix sent pública (la pantalla d'entrada en necessita la imatge del centre), però sense els llindars
  ni el curs; el professorat i l'administració en reben la versió completa. El frontend recarrega la configuració en entrar i en sortir.
- **Intents de contrasenya (6).** Tres límits: 5 fallades per compte *des d'una mateixa adreça*, 30 per compte en total (atac repartit) i 20 per adreça.
- **Fitxers (9).** `GET /api/files/{id}/download` serveix només `image/png|jpeg|gif|webp` amb el seu tipus; la resta, com `application/octet-stream`,
  sempre `attachment`, `X-Content-Type-Options: nosniff` i `Content-Security-Policy: sandbox`.
- **Estat de les aules (12).** Un professor només hi pot accedir si té algun examen a l'aula; i no en veu les adreces IP, les diferències de fitxers ni el resultat de
  les restauracions.

## Pendent a la VM

La VM només és accessible des de la xarxa de l'institut, així que s'han de fer **des d'una connexió a la VM** (SSH). Els tres primers no necessiten `sudo`; els altres sí.

```
cd ~/exam-platform
umask 077
chmod 600 infra/.env                                   # secrets: només el propietari
chmod 700 backups ~/backups-manual infra/tls/certs     # còpies de la BD i autoritat de certificació
find backups ~/backups-manual -type f -exec chmod 600 {} +
```

Després de desplegar la versió nova (perquè el servei de còpies tingui el grup 101 i pugui llegir els fitxers), amb `sudo`:

```
sudo chmod 750 /opt/exam-files /opt/exam-scripts       # els lliuraments dels alumnes, només per al backend i el seu grup
docker compose -f docker-compose.yml -f docker-compose.https.yml exec backup /backup/fes-copia.sh   # comprova que la còpia continua funcionant
```

I, el més important, limitar l'accés a la xarxa de l'institut:

```
sudo ./infra/restringeix-xarxa.sh --prova              # mostra què faria, sense canviar res
sudo ./infra/restringeix-xarxa.sh                      # ho aplica (rang per defecte: 10.100.0.0/16)
```

Quan facis còpies manuals de la BD, fes-les amb `umask 077` per no crear fitxers llegibles per tothom:
`(umask 077; docker exec infra-db-1 pg_dump -U examuser -Fc examplatform > ~/backups-manual/nom.dump)`.

## Socket de Docker (risc acceptat)

El backend llança contenidors per executar el codi dels alumnes, i per això té el socket de Docker, que equival a ser `root` a la VM. El sandbox dels contenidors
està ben tancat (sense xarxa, `cap-drop=ALL`, `no-new-privileges`, usuari sense privilegis, límits de memòria, CPU i processos, sortida limitada, volum en lectura),
però si algú aconseguís executar codi *al backend* (no als contenidors), tindria la VM. Un proxy del socket no ho arregla: filtra rutes de l'API però no les opcions de
cada contenidor (podria crear un contenidor privilegiat). La solució de fons és que el backend parli amb un **Docker `rootless`** dedicat a l'execució, de manera que
un compromís només doni accés a un usuari sense privilegis. És una tasca d'infraestructura (instal·lar `docker-ce-rootless-extras` i canviar el socket del backend)
que no s'ha fet; mentre no es faci, la mesura que més el redueix és limitar l'accés a la VM (punt 1) i mantenir el backend al dia.

## Què no cobreix

- Algú amb permisos d'administrador en un ordinador de l'aula pot desfer-hi la configuració d'examen: vegeu `usuari-examen-linux.md` (reclonar abans de cada examen
  i comprovar la integritat).
- El JWT viu a `localStorage` del navegador: una XSS el robaria. No n'hi ha cap de coneguda (React escapa el contingut, el Markdown no admet HTML cru, la CSP ja no permet
  scripts en línia i `connect-src 'self'` impedeix enviar-lo a fora), però és l'únic que quedaria entre una XSS i una sessió.
- Cap mesura tècnica no substitueix la vigilància a l'aula.

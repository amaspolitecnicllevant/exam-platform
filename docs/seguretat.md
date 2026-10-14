# Seguretat: auditoria i estat

Resum de l'auditoria de seguretat del 9 d'octubre de 2026 i de com s'ha tancat cada troballa. No és una prova d'intrusió: és una revisió del
codi, la configuració, la infraestructura i els scripts. L'autorització dels serveis s'ha revisat per mostreig, i no s'han pogut escanejar les
imatges Docker. Les dependències de Maven i de npm s'han escanejat el 10 d'octubre de 2026 amb la base de dades OSV (punt 15).

**Llegenda:** ✅ fet al codi o a la documentació · 🔧 requereix una acció a la VM (vegeu «Pendent a la VM») · 📝 risc acceptat o recomanació.

| # | Gravetat | Troballa | Estat |
|--:|:--|:--|:--|
| 1 | Alta | La VM és accessible des de qualsevol xarxa (Docker publica 3000/443 saltant-se UFW) | 🔧 `infra/restringeix-xarxa.sh` ja existeix i cal executar-lo; `prepara-vm.sh` accepta `XARXA_PERMESA` per a instal·lacions noves |
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
| 16 | Mitjana | `GET /api/matricules?modulId=…&curs=…` i `?alumneId=…` no filtraven per usuari: un professor podia llistar els alumnes de qualsevol mòdul o les matrícules de qualsevol alumne (només la consulta sense paràmetres estava limitada) | ✅ Totes les consultes de matrícules es limiten als mòduls que imparteix el professor (l'administració ho veu tot) |
| 17 | Mitjana | Un professor veia noms i correus de tots els usuaris (`GET /api/users`) i podia afegir a un grup o a un examen qualsevol alumne | ✅ Només els alumnes de les seves assignatures, dels seus grups i els que ha creat; també es comprova en afegir alumnes a grups i exàmens |
| 18 | Mitjana | Imatges de Docker amb vulnerabilitats (escaneig amb Trivy): `docker-cli` del backend (2 altes) i `libexpat`, `pcre2` i `tiff` de nginx (3 altes) | ✅ Els `Dockerfile` fan `apk upgrade`. Verificat: el frontend queda sense cap crítica ni alta, i el backend només conserva `spring-webmvc` (vegeu «Dependències») |
| 19 | Baixa | `postgres:16-alpine` conté 25 vulnerabilitats altes/crítiques, totes al binari `gosu` (compilat amb una versió antiga de Go) | 📝 Risc acceptat: `gosu` només s'executa en arrencar el contenidor per deixar de ser `root` i no rep dades de ningú. S'arreglarà quan l'imatge oficial es reconstrueixi (`docker compose pull db backup`) |
| 20 | Mitjana | Cap límit general de peticions: una sola adreça podia saturar el servidor | ✅ nginx limita per adreça (429): tota l'API a 60 peticions/s (ràfega de 200), el login a 5/s (ràfega de 50) i els informes d'ordinadors a 2/s; màxim 100 peticions simultànies. Provat amb Docker |
| 21 | Mitjana | Ningú s'assabentava d'un atac a un compte, d'un administrador entrant des d'una adreça nova o d'informes d'ordinadors rebutjats: no es registraven els inicis de sessió fallits ni hi havia avisos | ✅ S'afegeixen esdeveniments a l'auditoria i alertes calculades per als administradors, visibles a totes les pàgines |
| 15 | Mitjana | Dependències amb vulnerabilitats conegudes (Tomcat 10.1.55, Jackson 2.21.4, driver de PostgreSQL 42.7.11, log4j-api 2.24.3) | ✅ Actualitzades a Tomcat 10.1.60, Jackson 2.21.7, PostgreSQL 42.7.14 i log4j-api 2.25.5 (`backend/pom.xml`). Queden 2 de `spring-webmvc` (SSE i XSLT, que no fem servir) |

## Revisió de l'autorització (10 d'octubre)

S'han inventariat tots els endpoints (rol de cada un) i, per a cada servei que rep un identificador, s'ha comprovat que hi hagi una comprovació de propietat. Tot el que és
del professorat comprova que l'examen sigui seu o del seu mòdul (`assertOwnership`), els grups i les invitacions són del seu creador, i l'alumne només accedeix a la seva sessió,
les seves respostes i els seus fitxers. La importació d'usuaris força el rol d'alumne i només deixa matricular en mòduls que el professor imparteix. Les imatges dels enunciats
es validen (llista blanca, signatura del fitxer i mida). L'única troballa real ha estat la dels filtres de matrícules (punt 16).

**Decisions i riscos que queden (no s'han canviat perquè afecten el funcionament):**
- ~~Un professor veu tots els usuaris~~ → **resolt (punt 17).** Ara un professor veu només els alumnes de les **assignatures (mòduls) que imparteix**, els dels grups que ha creat, els alumnes
  que ha donat d'alta ell mateix (`users.creat_per`, migració V40) i a si mateix. No veu la resta de l'alumnat ni el personal, i tampoc no pot afegir a un grup o a un examen un alumne que
  no veu. Conseqüència: un alumne d'una convalidació o d'una recuperació d'un altre mòdul, que el professor no imparteix, l'ha de matricular l'administració abans.
- **`GET /api/exams/{id}` per a un alumne** deixa veure un examen publicat encara que no sigui dels destinataris, si es coneix l'UUID (que no es pot endevinar). Es podria exigir
  la mateixa elegibilitat que per començar-lo; no s'ha fet perquè hi ha casos d'accés (exàmens sense mòdul, programats) que no es poden provar sense navegador.
- **La BD s'accedeix amb el superusuari de PostgreSQL** (`POSTGRES_USER`). Un rol propi sense privilegis de superusuari limitaria el dany d'una injecció SQL (no n'hi ha cap de coneguda: totes
  les consultes són parametritzades). És un canvi de la BD en producció que cal fer amb cura.
- Registre per invitació: qui té l'enllaç pot crear un compte d'alumne amb qualsevol correu (no es verifica). L'enllaç caduca i té un màxim d'usos.

## Dependències

Les versions corregides es fixen a `backend/pom.xml` perquè Spring Boot 3.5.16 (l'última 3.5.x) porta les antigues. Es poden treure quan una versió de Spring Boot ja
les inclogui o en tingui de més noves. Per tornar a escanejar: `mvn dependency:list -DincludeScope=runtime` i consultar l'API d'OSV (`https://api.osv.dev/v1/querybatch`), i
`npm audit --omit=dev` al frontend. Només queden dues vulnerabilitats de `spring-webmvc` 6.2.19 (corregides a Spring 7): afecten el flux d'esdeveniments (SSE) i `XsltView`,
que l'aplicació no fa servir, i dues de `react-router` (redirecció oberta amb una URL de l'usuari i la hidratació de SSR), que no són explotables aquí.

## Alertes de seguretat

Els administradors veuen una barra a la part de dalt de totes les pàgines (se n'actualitzen cada 5 minuts) amb les alertes actives, i el detall en prémer *Veure-les*. Es calculen a partir de l'auditoria
(`GET /api/admin/alertes`, només administradors) i **no envien res per correu** (no n'hi ha); desapareixen soles quan els esdeveniments surten de la finestra de cada regla.

| Alerta | Quan salta | Gravetat |
|:--|:--|:--|
| Atac a un compte | 5 inicis de sessió fallits al mateix compte en 1 h | Alta si és del personal, mitjana si és d'un alumne |
| Atac des d'una adreça | 10 fallits des d'una mateixa adreça en 1 h | Alta si prova 5 comptes o més; mitjana si no |
| Comptes bloquejats | el límit d'intents ha bloquejat algú (24 h) | Mitjana |
| Administrador des d'una adreça nova | un administrador entra des d'una adreça des de la qual no havia entrat mai (7 dies; el seu primer inici de sessió no compta) | Mitjana |
| Informes d'ordinadors amb testimoni incorrecte | algú envia informes sense el testimoni correcte (24 h) | Mitjana |
| Informes des d'una adreça fora d'aula | l'adreça no és el CIDR de cap aula (24 h) | Informació |
| Restauracions d'ordinadors fallides | una restauració demanada des de l'aplicació ha fallat a l'ordinador (24 h) | Mitjana |
| Moltes contrasenyes reinicialitzades | 3 o més reinicialitzacions de contrasenya o sessions tancades en 1 h | Mitjana |
| Ordinadors alterats | algun ordinador no coincideix amb la referència | Alta |

**Què es registra a l'auditoria** (i es pot filtrar a *Auditoria*): `LOGIN_FALLIT` (el compte escrit, mai la contrasenya), `LOGIN_BLOQUEJAT` (un cop per compte i adreça cada 15 minuts), `LOGIN_OK` (només professorat i administració),
`LOGIN_NOVA_IP`, `EQUIP_TESTIMONI_INVALID` i `EQUIP_IP_FORA_D_AULA` (un cop per adreça cada 10 minuts). Els límits eviten que un atac ompli la taula d'auditoria.

**Què fer quan salta una alerta alta:** si és un atac a un compte del personal, mira l'auditoria per veure des de quines adreces; el compte ja queda bloquejat per al que ataca, i si hi ha dubtes, reinicialitza-li la contrasenya (això tanca totes les sessions
que tingués). Si un administrador ha entrat des d'una adreça nova i no era ell, tanca-li les sessions (*Usuaris → Tancar sessions*) i canvia-li la contrasenya.

## Límits de peticions (nginx)

`frontend/nginx-limits.conf` (zones) i `frontend/nginx-comu.conf` (rutes) limiten per adreça, i s'apliquen igual en HTTP i en HTTPS. Una persona normal no hi arriba (l'autodesat d'un examen són 1-2 peticions per
segon i carregar la pàgina unes quantes desenes d'un cop). **Cas que cal conèixer:** si molts usuaris surten a la xarxa amb **la mateixa adreça** (NAT o proxy), compten com un de sol i es podrien tallar.
Als ordinadors de l'aula cadascú té la seva adreça, però si això canvia, cal pujar el `rate` de la zona `api` a `nginx-limits.conf` (i el `burst` de `nginx-comu.conf`) i reconstruir el frontend.
Els tallats es veuen al registre d'nginx (`docker compose logs frontend | grep limiting`). La prova és a `infra/prova-seguretat/prova_nginx.py`.

## Imatges de Docker

`infra/escaneja-imatges.sh` escaneja amb Trivy totes les imatges de la plataforma (les pròpies, la BD i les del sandbox d'execució). Resultat del 10 d'octubre de 2026: `bash:5` i
`eclipse-temurin` (jre i jdk) sense cap vulnerabilitat crítica o alta; la imatge del backend només conserva `spring-webmvc` (vegeu «Dependències»); la del frontend, cap; i `postgres:16-alpine`
té les de `gosu` (punt 19). Les vulnerabilitats de paquets del sistema es corregeixen reconstruint **sense la memòria cau** de Docker, i convé fer-ho cada mes:
`docker compose build --pull --no-cache backend frontend`. També `docker compose pull db backup` per tenir la imatge de PostgreSQL més recent.

## Prova automàtica

`infra/prova-seguretat/` conté una prova de 40 comprovacions per HTTP que s'executa contra una instància local temporal (mai contra producció): configuració pública, autorització,
revocació de sessions, contrasenyes, bloqueig per compte i adreça, usuari inhabilitat, informes d'ordinadors i restauració, i entrades dolentes. Convé repetir-la després de tocar
qualsevol cosa de seguretat.

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

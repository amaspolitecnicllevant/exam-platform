# Usuaris d'examen als ordinadors de l'aula (Linux Mint / Ubuntu amb LightDM)

Aquesta guia explica com preparar cada ordinador de l'aula perquè els alumnes facin l'examen amb un **usuari d'examen** que,
**encara que en sàpiguen la contrasenya**, no pot deixar res gravat, no pot obrir altres programes (perfil quiosc) i no pot
muntar un pendrive.

> **Avís important.** L'script d'aquest document **no s'ha provat en cap ordinador real** (ni s'ha comprovat la sintaxi). Hi ha
> parts que depenen de la versió de LightDM, del gestor de finestres i del navegador. **Prova'l primer en UN sol ordinador** i no el
> repeteixis a la resta fins que la llista de la secció 4 surti bé.

## 1. Què es crea

A cada ordinador cal crear **una sèrie d'usuaris per als exàmens**. L'script en crea un cada vegada que l'executes:

| Usuari (nom suggerit) | Perfil | Per a què |
|:--|:--|:--|
| `examen` | **quiosc** | Exàmens **sense fitxers**. L'alumne només veu el navegador a pantalla completa amb la plataforma. Sense escriptori, menús, terminal ni programes. Tancar el navegador tanca la sessió |
| `examen-fitxers` | **escriptori** | Exàmens **amb lliurament de fitxer** (Word, Packet Tracer…): té l'escriptori i els programes de l'ordinador, però també sense permisos i amb la casa en memòria |

Tots dos usuaris tenen:

- **Casa a la memòria (tmpfs)**, que es buida a cada sessió: tot el que l'alumne hi gravi (un document, una descàrrega, una captura)
  **desapareix en tancar la sessió**. En el perfil quiosc, a més, no s'hi pot executar res (`noexec`).
- **Sense shell** (`nologin`): no es pot entrar per consola ni per SSH amb aquest usuari.
- **Sense grups ni `sudo`**, i amb **totes les accions de polkit prohibides** (no pot muntar discs ni pendrives, ni apagar, ni canviar la xarxa).
- **Sense `cron` ni `at`**.
- **Menú d'entrada (opcional, perfil quiosc):** amb `--menu` o `--isard-url`, en entrar surt un petit menú amb **Plataforma d'exàmens**,
  **Isard** (si s'ha indicat l'adreça) i **Aturar l'ordinador**. El menú surt **un cop per sessió**: en triar una opció s'obre el navegador, i
  en tancar-lo s'acaba la sessió (i es buida la casa). No es torna al menú sense acabar la sessió, perquè sinó el següent alumne trobaria la
  sessió oberta de l'anterior. Per poder apagar, aquest usuari té una **única excepció** a la regla de polkit: apagar l'ordinador (no
  reiniciar, ni res més).
- **Perfil quiosc**: la sessió no és l'escriptori sinó només el navegador; les **tecles de canvi de consola** (Ctrl+Alt+F1…) i
  Ctrl+Alt+Retrocés es desactiven per a aquesta sessió.

La **sortida a internet** ja la limiteu amb el programa de l'institut: aquest script no la toca.

**Què NO fa:** no pot detectar ni impedir des del navegador què hi ha obert en un altre usuari, ni res fora de l'ordinador. Tampoc no
protegeix de l'accés físic (reiniciar des d'un USB, canviar la BIOS): per a això, contrasenya de la BIOS i arrencada només des del disc.
Els alumnes continuen tenint el seu usuari habitual per a la resta de classes.

## 2. Requisits (a cada ordinador)

- Linux Mint o Ubuntu amb **LightDM** com a gestor d'inici de sessió (a Mint ho és per defecte).
- **Firefox en `.deb`** (no en `snap`) o **Chrome/Chromium**. El Firefox en `snap` no funciona amb una casa en memòria; l'script ho detecta i s'atura.
- Connexió a internet **només durant la instal·lació** (instal·la `openbox`, `x11-xkb-utils`, `x11-xserver-utils` i `libnss3-tools`).
- El fitxer **`ca.crt`** de la CA del centre (a la VM és a `~/exam-platform/infra/tls/certs/ca.crt`; vegeu [https.md](https.md)). Se li dona a
  l'script perquè el navegador de l'usuari d'examen confiï en la plataforma (la casa és nova a cada sessió, així que no pot "recordar-ho").
- L'adreça de la plataforma, per exemple `https://examens.politecnicllevant.cat:3443`.

## 3. Instal·lar-ho

Fes-ho amb un usuari administrador de l'ordinador.

1. Copia el `ca.crt` a l'ordinador (per exemple a `~/ca.crt`).
2. Crea el fitxer `prepara-usuari-examen.sh` amb **el contingut de l'apartat 6** d'aquest document i dona-li permís d'execució:
   `chmod +x prepara-usuari-examen.sh`.
3. Crea l'usuari de **quiosc** (demanarà la contrasenya que els alumnes faran servir; **no ha de ser la de cap usuari real**):

   ```
   sudo ./prepara-usuari-examen.sh --url https://examens.politecnicllevant.cat:3443 --ca ~/ca.crt
   ```

   Amb el **menú** (Plataforma d'exàmens / Isard / Aturar l'ordinador):

   ```
   sudo ./prepara-usuari-examen.sh --url https://examens.politecnicllevant.cat:3443 --ca ~/ca.crt \
        --isard-url https://isard.politecnicllevant.cat
   ```

   (Només `--menu`, sense `--isard-url`, mostra el menú sense l'entrada d'Isard.) Torna a executar-lo amb aquestes opcions per afegir el menú
   a un usuari que ja existeix; no cal reiniciar si ja has reiniciat abans.

   Per defecte l'usuari és `examen`, el perfil `quiosc` i el navegador `firefox`. Per a Chrome: `--navegador chrome`.
4. Crea l'usuari per als **exàmens amb fitxers** (perfil escriptori):

   ```
   sudo ./prepara-usuari-examen.sh --perfil escriptori --usuari examen-fitxers
   ```

5. **Reinicia l'ordinador** (`sudo reboot`). **No et saltis aquest pas:** LightDM només llegeix la seva configuració quan arrenca, i
   mentre no es reiniciï no farà servir el mode quiosc (l'usuari entraria amb un escriptori complet i podria fallar l'entrada).

L'script es pot tornar a executar sense problema (per canviar la contrasenya, la URL o el navegador). Després de tornar-lo a executar
amb una configuració de LightDM nova, també cal reiniciar.

## 4. Prova-ho en UN ordinador abans de continuar

**Reinicia l'ordinador**, i a la pantalla d'entrada entra com a `examen` (perfil quiosc). **L'usuari no surt a la llista** (els usuaris sense shell
s'hi amaguen): tria l'opció d'entrar un altre usuari («Login» / «Altre…») i escriu-hi `examen` i la contrasenya. Comprova:

- [ ] Només surt el **navegador a pantalla completa**, amb la plataforma, **sense avisos de certificat**.
- [ ] No hi ha escriptori, barra, menús, ni es pot obrir cap terminal (prova el clic dret i Ctrl+Alt+T).
- [ ] **Ctrl+Alt+F3** no canvia a una consola.
- [ ] **Si has posat el menú:** surt el menú centrat. «Plataforma d'exàmens» i «Isard» obren el navegador a l'adreça correcta; «Aturar l'ordinador» l'apaga;
  Esc al menú acaba la sessió.
- [ ] Entres a la plataforma, fas una cosa de prova, **tanques el navegador** i tornes a la pantalla d'entrada.
- [ ] En tornar a entrar com a `examen`, **no queda res de la sessió anterior** (ni descàrregues, ni historial, ni sessió oberta).
- [ ] Un pendrive endollat **no es munta** ni apareix.
- [ ] Amb la sessió d'un professor oberta en una altra consola, `ls /home/examen` des d'aquella sessió: ha d'estar **buit** (o no existir el contingut) quan ningú és dins.

Després, entra com a `examen-fitxers` i comprova que hi ha l'escriptori i els programes, que pots desar un document, i que **en tancar
la sessió i tornar a entrar el document ja no hi és**.

Si res d'això no passa, **no ho repeteixis a la resta** i digues-ho (pot dependre de la versió de LightDM o del navegador).

## 5. Treure-ho

```
sudo ./prepara-usuari-examen.sh --usuari examen --desfes                      # treu la configuració
sudo ./prepara-usuari-examen.sh --usuari examen --desfes --esborra-usuari     # i esborra l'usuari
```

Quan es treu l'últim usuari d'examen, també es treuen els fitxers comuns i la línia afegida a `/etc/pam.d/lightdm`.

**Si algú no pot entrar a l'ordinador després d'instal·lar-ho**, la causa més probable és la línia afegida a `/etc/pam.d/lightdm`
(porta el comentari `# examen-plataforma`). Es pot esborrar des d'una consola (Ctrl+Alt+F3 amb un usuari administrador) o per SSH, i ja
no es munta cap casa en memòria.

## 5b. Comprovar la integritat abans de l'examen

Si hi ha alumnes amb permisos d'administrador als ordinadors, **poden desfer tot això abans de l'examen** (esborrar la línia de PAM, canviar
els scripts, deixar fitxers a la casa…). Per això, abans de cada examen, convé comparar cada ordinador amb una referència neta. Es fa des d'un
ordinador d'administració (no als ordinadors de l'aula), llegint-los per SSH. Fa servir el mateix script que l'informe de l'apartat 5c
(`/usr/local/sbin/examen-comprova`, que l'instal·la `prepara-usuari-examen.sh`), i per això dona el mateix resultat; la pantalla *Aules* de
l'aplicació fa aquesta mateixa comparació de manera automàtica.

**Limitació:** qui té `root` a l'ordinador també podria falsejar la resposta de la comprovació (per exemple, canviant `sha256sum`). Això detecta
descuits i manipulacions normals, **no a algú que sàpiga amagar-ho**. L'opció més robusta és **reclonar** els ordinadors des de la imatge abans
de l'examen; aquesta comprovació és un complement. Cal fer-la **just abans de l'examen**. Si algú té una sessió oberta com a `examen`, la seva
casa (en memòria) no es mira, així que no surt cap falsa alerta.

**Què es compara** (tot el que fa que el mode tancat funcioni): `/etc/pam.d/lightdm`, tot `/etc/lightdm`, la regla de polkit, tot `/etc/examen`,
els scripts `/usr/local/sbin/examen-*`, `cron.deny`/`at.deny`, `sudoers` i `sudoers.d`; a més, els usuaris d'examen (amb la seva shell i els seus
grups), els membres dels grups `sudo` i `adm`, i que les cases dels usuaris d'examen siguin buides.

**1. Preparar-ho (un cop).** Al terminal de l'ordinador d'administració, amb la llista d'ordinadors a `ordinadors.txt`:

```
read -r -s -p "Contrasenya de sudo d'lmadmin: " SPW; echo
COMPROVA='/usr/local/sbin/examen-comprova'
```

**2. Crear la referència** a partir d'un ordinador **net i validat** (el de prova, un cop acabada la llista de comprovació). Es desa **fora dels
ordinadors de l'aula**: a l'ordinador d'administració, i convé guardar-ne una còpia també al servidor.

```
printf '%s\n' "$SPW" | ssh lmadmin@10.100.94.19 "sudo -S -p '' bash -c '$COMPROVA'" > referencia-integritat.lst
sha256sum referencia-integritat.lst
```

Apunta el segon resultat (el resum) a un lloc segur. Si has de tornar a instal·lar alguna cosa (canvis a l'script, contrasenya, etc.), refés la referència.

**3. Comprovar l'aula** (abans de cada examen, amb els ordinadors reiniciats):

```
for ip in $(cat ordinadors.txt); do
  printf '%s\n' "$SPW" | ssh -o ConnectTimeout=5 lmadmin@$ip "sudo -S -p '' bash -c '$COMPROVA'" > /tmp/integritat-$ip.lst 2>/dev/null
  if [ ! -s /tmp/integritat-$ip.lst ]; then echo "$ip: NO RESPON"
  elif cmp -s /tmp/integritat-$ip.lst referencia-integritat.lst; then echo "$ip: OK"
  else echo "$ip: ALTERAT"; diff referencia-integritat.lst /tmp/integritat-$ip.lst | head -10
  fi
done
```

**Si algun surt ALTERAT:** les línies del `diff` diuen què ha canviat (un fitxer, un grup, alguna cosa a la casa…). La manera segura d'arreglar-ho és
**reclonar** aquell ordinador. Tornar a executar l'script restaura els seus fitxers però no treu res d'afegit (un fitxer nou, un usuari al grup `sudo`).

## 5c. Estat dels ordinadors a l'aplicació (pantalla *Aules*)

L'aplicació pot mostrar si els ordinadors de cada aula estan preparats, **sense connectar-s'hi ni guardar-ne cap credencial**: són els ordinadors
els que envien cada 15 minuts un petit informe a la plataforma (el nom, si arriben a la plataforma i a Isard, el navegador, l'espai de disc, i
la llista d'integritat de l'apartat 5b). L'aplicació l'assigna a l'aula per la **IP d'origen** (el CIDR de l'aula), així que un ordinador només pot
informar de si mateix, des de la seva xarxa.

**Què veu cada rol**
- **Els professors**, a la llista d'exàmens amb aula: un avís si hi ha ordinadors que **fa temps que no s'encenen** (per defecte, més de 7 dies sense cap
  informe), amb el seu nom, perquè **demanin que els encenguin** abans de l'examen i es puguin comprovar. També avisa dels ordinadors alterats o que no
  arriben a la plataforma.
- **Els administradors**, a *Aules → Ordinadors*: la taula completa, els avisos (poc disc, Isard que no respon…), què ha canviat en un ordinador
  alterat, i els botons **Fixar com a referència** i **Esborrar**.

**Estats d'un ordinador:** *Preparat*; *Alterat* (la configuració no coincideix amb la referència); *No arriba a la plataforma*; *Apagat o sense
informar* (fa més de 45 minuts que no informa: és normal fora d'hora); *Fa N dies que no s'encén*; *Sense referència* (encara no s'ha fixat cap ordinador de referència).

**Activar-ho (un cop)**
1. A la plataforma (`infra/.env`), posa un testimoni: `EQUIPS_TOKEN=` el resultat de `openssl rand -hex 24`. Aplica-ho amb `docker compose up -d backend`.
   Sense testimoni, la recepció d'informes està desactivada.
2. A cada ordinador, executa l'script amb el testimoni (millor per variable d'entorn, perquè no quedi a l'historial):

   ```
   sudo EXAMEN_INFORME_TOKEN='el-testimoni' ./prepara-usuari-examen.sh --url https://examens.politecnicllevant.cat:3443 --ca ~/ca.crt \
        --isard-url https://isard.politecnicllevant.cat
   ```

   (Amb `--informe-token` també funciona, però el testimoni queda a l'historial i a la llista de processos.) Instal·la `examen-informa` i un temporitzador
   de systemd que s'activa 2 minuts després d'arrencar i cada 15 minuts. Si els ordinadors només tenen el perfil `escriptori`, afegeix `--informe-url` amb l'adreça de la plataforma.
3. Als pocs minuts, l'ordinador surt a *Aules → Ordinadors*. Quan un ordinador net i validat (el de prova) hi sigui, prem **Fixar com a referència** a la seva fila:
   a partir d'aquí, tots els altres es comparen amb ell. Si canvies alguna cosa a l'script, torna a fixar la referència.

**Seguretat i límits**
- El testimoni és **compartit per tots els ordinadors**. Un ordinador no l'envia a la línia d'ordres (queda en un fitxer de `root`, `/etc/examen/informe.conf`),
  però un administrador d'aquell ordinador el pot llegir. Per això l'aplicació **només accepta informes des de la xarxa d'una aula**, limita la freqüència
  per IP i el nombre d'ordinadors per aula, i no guarda res més que l'últim informe de cada ordinador. Si el testimoni es filtra, canvia'l a `.env` i
  torna a executar l'script a tots els ordinadors.
- Qui té `root` en un ordinador pot **falsejar el seu informe** (només el d'aquell ordinador) o deixar d'enviar-lo; el segon cas surt com a «sense notícies».
  Un ordinador que menteixi bé no es pot detectar des d'aquí: la garantia més forta continua sent **reclonar** abans de l'examen.
- Un alumne podria inventar-se ordinadors falsos a la seva aula (amb noms que no existeixen). Surten a la llista i es poden esborrar des de *Aules → Ordinadors*.

## 6. L'script

Guarda'l com a `prepara-usuari-examen.sh`.

```bash
#!/usr/bin/env bash
# Prepara un ordinador de l'aula (Linux Mint / Ubuntu amb LightDM) amb un usuari «d'examen» que, encara que
# els alumnes en sàpiguen la contrasenya, no pot deixar res gravat ni sortir del que se li permet.
#
# Dos perfils (es poden crear tots dos, amb noms diferents):
#   quiosc      L'usuari només té el navegador a pantalla completa amb la plataforma. Sense escriptori, menús,
#               terminal ni programes; tancar el navegador tanca la sessió. Per als exàmens sense fitxers.
#   escriptori  Escriptori normal amb els programes de l'ordinador (Word, Packet Tracer…), per als exàmens amb
#               lliurament de fitxer. Se li treuen els permisos, però no els programes.
# El perfil quiosc pot tenir un MENÚ en entrar (--menu / --isard-url): Plataforma d'exàmens, Isard i Aturar l'ordinador.
# Amb --informe-token, l'ordinador també informa cada 15 minuts del seu estat a la plataforma (pantalla Aules).
# Tots dos: casa a la memòria (tmpfs) que es buida a cada sessió (res no es conserva), sense shell, sense grups,
# sense permisos de polkit (no pot muntar un pendrive) i sense cron/at.
#
# S'executa com a root, un cop a cada ordinador:
#     sudo ./prepara-usuari-examen.sh --url https://examens.centre.cat:3443 --ca ca.crt
#     sudo ./prepara-usuari-examen.sh --perfil escriptori --usuari examen-fitxers
#     sudo ./prepara-usuari-examen.sh --usuari examen --desfes            (ho desfà; --esborra-usuari l'esborra)
# Es pot tornar a executar sense problema (és idempotent).
set -euo pipefail

USUARI=examen
PERFIL=quiosc
URL=""
NAVEGADOR=firefox
CA=""
MENU=0
ISARD_URL=""
INFORME_URL=""
INFORME_TOKEN="${EXAMEN_INFORME_TOKEN:-}"
CONTRASENYA="${EXAMEN_CONTRASENYA:-}"
DESFES=0
ESBORRA_USUARI=0
P="${PREFIX:-}"            # només per a proves: arrel falsa on escriure (no crea usuaris ni munta res)

ús() {
  cat <<EOF
Ús: sudo $0 [opcions]
  --usuari NOM        Nom de l'usuari (per defecte: examen)
  --perfil quiosc|escriptori   (per defecte: quiosc)
  --url URL           Adreça de la plataforma (obligatòria al perfil quiosc), p. ex. https://examens.centre.cat:3443
  --navegador firefox|chrome|chromium   Perfil quiosc (per defecte: firefox)
  --ca FITXER         ca.crt del centre, perquè el navegador de l'usuari confiï en la plataforma
  --menu              Perfil quiosc: en entrar, un menú (Plataforma d'exàmens / Aturar l'ordinador)
  --isard-url URL     Afegeix «Isard» al menú (i activa el menú), p. ex. https://isard.centre.cat
  --informe-url URL   Adreça de la plataforma on enviar l'informe d'estat (per defecte, la de --url)
  --informe-token T   Testimoni (EQUIPS_TOKEN de la plataforma); o variable EXAMEN_INFORME_TOKEN. Activa l'informe
  --contrasenya PWD   Contrasenya de l'usuari (o variable EXAMEN_CONTRASENYA; si no, la demana)
  --desfes            Treu la configuració d'aquest usuari (amb --esborra-usuari també l'usuari)
EOF
}
error() { echo "ERROR: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --usuari)      USUARI="${2:?}"; shift 2 ;;
    --perfil)      PERFIL="${2:?}"; shift 2 ;;
    --url)         URL="${2:?}"; shift 2 ;;
    --navegador)   NAVEGADOR="${2:?}"; shift 2 ;;
    --ca)          CA="${2:?}"; shift 2 ;;
    --menu)        MENU=1; shift ;;
    --isard-url)   ISARD_URL="${2:?}"; MENU=1; shift 2 ;;
    --informe-url)   INFORME_URL="${2:?}"; shift 2 ;;
    --informe-token) INFORME_TOKEN="${2:?}"; shift 2 ;;
    --contrasenya) CONTRASENYA="${2:?}"; shift 2 ;;
    --desfes)      DESFES=1; shift ;;
    --esborra-usuari) ESBORRA_USUARI=1; shift ;;
    -h|--help)     ús; exit 0 ;;
    *) ús >&2; error "opció desconeguda: $1" ;;
  esac
done

[[ "$USUARI" =~ ^[a-z][a-z0-9_-]{0,30}$ ]] || error "nom d'usuari no vàlid: $USUARI"
case "$USUARI" in root|administrador) error "no es pot fer servir l'usuari $USUARI" ;; esac
case "$PERFIL" in quiosc|escriptori) ;; *) error "perfil no vàlid: $PERFIL (quiosc o escriptori)" ;; esac
case "$NAVEGADOR" in firefox|chrome|chromium) ;; *) error "navegador no vàlid: $NAVEGADOR" ;; esac
if [ "$MENU" -eq 1 ] && [ "$PERFIL" != quiosc ]; then error "el menú només és per al perfil quiosc"; fi
[ -n "$INFORME_URL" ] || INFORME_URL="$URL"
if [ -n "$INFORME_TOKEN" ]; then
  [[ "$INFORME_URL" =~ ^https://[^[:space:]\"\'\\\$\`]+$ ]] || error "l'informe necessita --informe-url (o --url) amb https://"
  [[ "$INFORME_TOKEN" =~ ^[A-Za-z0-9._~+/=-]+$ ]] || error "el testimoni només pot portar lletres, xifres i . _ ~ + / = -"
fi
if [ -z "$P" ] && [ "$(id -u)" -ne 0 ]; then error "cal executar-lo com a root (sudo)"; fi

CONF_DIR="$P/etc/examen"
USUARIS_DIR="$CONF_DIR/usuaris.d"
PAM_FITXER="$P/etc/pam.d/lightdm"
PAM_LINIA="session required pam_exec.so quiet /usr/local/sbin/examen-pam"
LIGHTDM_CONF="$P/etc/lightdm/lightdm.conf.d/90-examen.conf"
POLKIT_REGLA="$P/etc/polkit-1/rules.d/49-examen.rules"
MARCA="# examen-plataforma"

usuaris_configurats() { ls "$USUARIS_DIR" 2>/dev/null || true; }

# ── Regles que depenen de la llista d'usuaris (es regeneren a cada execució) ──
escriu_llistes() {
  mkdir -p "$(dirname "$POLKIT_REGLA")"
  local llista aturar="" u
  llista=$(usuaris_configurats | sed 's/.*/"&"/' | paste -sd, -)
  if [ -z "$llista" ]; then rm -f "$POLKIT_REGLA"; return; fi
  # Només els usuaris de quiosc amb menú poden apagar l'ordinador (és l'opció «Aturar l'ordinador»)
  for u in $(usuaris_configurats); do
    if ( . "$USUARIS_DIR/$u"; [ "${MENU:-0}" = 1 ] ); then aturar="$aturar\"$u\","; fi
  done
  aturar="${aturar%,}"
  cat >"$POLKIT_REGLA" <<EOF
// Els usuaris d'examen no poden fer cap acció privilegiada (muntar discs, canviar xarxa…).
// Excepció: els que tenen menú poden apagar l'ordinador (i només això).
var usuarisExamen = [$llista];
var usuarisAturar = [$aturar];
polkit.addRule(function(action, subject) {
  if (usuarisExamen.indexOf(subject.user) < 0) { return; }
  if (usuarisAturar.indexOf(subject.user) >= 0 && action.id.indexOf("org.freedesktop.login1.power-off") === 0) {
    return polkit.Result.YES;
  }
  return polkit.Result.NO;
});
EOF
  for f in "$P/etc/cron.deny" "$P/etc/at.deny"; do
    touch "$f"
    for u in $(usuaris_configurats); do grep -qx "$u" "$f" || echo "$u" >>"$f"; done
  done
}

# ── Desfer ────────────────────────────────────────────────────────────────────
if [ "$DESFES" -eq 1 ]; then
  rm -f "$USUARIS_DIR/$USUARI"
  if [ -z "$P" ] && mountpoint -q "/home/$USUARI" 2>/dev/null; then umount -l "/home/$USUARI" || true; fi
  if [ "$ESBORRA_USUARI" -eq 1 ] && [ -z "$P" ] && id "$USUARI" >/dev/null 2>&1; then userdel -r "$USUARI" 2>/dev/null || userdel "$USUARI"; fi
  for f in "$P/etc/cron.deny" "$P/etc/at.deny"; do [ -f "$f" ] && sed -i "/^$USUARI\$/d" "$f"; done
  escriu_llistes
  if [ -z "$(usuaris_configurats)" ]; then
    # Era l'últim: es treu tot el que és comú
    [ -f "$PAM_FITXER" ] && sed -i "\\|$MARCA|d" "$PAM_FITXER"
    if [ -z "$P" ]; then systemctl disable --now examen-informa.timer 2>/dev/null || true; fi
    rm -f "$LIGHTDM_CONF" "$P/usr/local/sbin/examen-pam" "$P/usr/local/sbin/examen-session-wrapper" \
          "$P/usr/local/sbin/examen-kiosk" "$P/usr/local/sbin/examen-informa" "$P/usr/local/sbin/examen-comprova" \
          "$P/etc/systemd/system/examen-informa.service" "$P/etc/systemd/system/examen-informa.timer" \
          "$CONF_DIR/informe.conf" "$CONF_DIR/openbox-rc.xml" "$CONF_DIR/wrapper-original"
    if [ -z "$P" ]; then systemctl daemon-reload 2>/dev/null || true; fi
    rmdir "$USUARIS_DIR" "$CONF_DIR" 2>/dev/null || true
  fi
  echo "Fet: configuració de «$USUARI» treta."
  exit 0
fi

# ── Comprovacions prèvies ─────────────────────────────────────────────────────
if [ -z "$P" ]; then
  command -v lightdm >/dev/null || error "no hi ha LightDM: aquest script és per a Linux Mint / Ubuntu amb LightDM"
fi
if [ "$PERFIL" = quiosc ]; then
  [ -n "$URL" ] || error "el perfil quiosc necessita --url"
  [[ "$URL" =~ ^https://[^[:space:]\"\'\\\$\`]+$ ]] || error "la URL ha de començar per https:// i no portar espais ni cometes: $URL"
  if [ -n "$ISARD_URL" ]; then
    [[ "$ISARD_URL" =~ ^https://[^[:space:]\"\'\\\$\`]+$ ]] || error "la URL d'Isard ha de començar per https:// i no portar espais ni cometes: $ISARD_URL"
  fi
  if [ -z "$P" ]; then
    case "$NAVEGADOR" in
      firefox)  command -v firefox >/dev/null || error "Firefox no està instal·lat" ;;
      chrome)   command -v google-chrome >/dev/null || command -v google-chrome-stable >/dev/null || error "Chrome no està instal·lat" ;;
      chromium) command -v chromium >/dev/null || command -v chromium-browser >/dev/null || error "Chromium no està instal·lat" ;;
    esac
    if [ "$NAVEGADOR" = firefox ] && command -v snap >/dev/null && snap list firefox >/dev/null 2>&1; then
      error "el Firefox d'aquest ordinador és un snap, que no funciona amb una casa en memòria. Instal·la el Firefox en .deb o fes servir Chrome"
    fi
  fi
fi
[ -z "$CA" ] || [ -f "$CA" ] || error "no trobo el fitxer de la CA: $CA"

if [ -z "$P" ] && [ "$PERFIL" = quiosc ]; then
  echo "==> Instal·lant els paquets necessaris"
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    openbox x11-xkb-utils x11-xserver-utils libnss3-tools zenity
fi

# ── L'usuari ──────────────────────────────────────────────────────────────────
if [ -z "$P" ]; then
  echo "==> Usuari $USUARI"
  if id "$USUARI" >/dev/null 2>&1; then
    usermod -s /usr/sbin/nologin -G "" "$USUARI"
  else
    useradd --create-home --home-dir "/home/$USUARI" --shell /usr/sbin/nologin --user-group "$USUARI"
  fi
  if [ -z "$CONTRASENYA" ]; then
    read -r -s -p "Contrasenya de l'usuari $USUARI: " CONTRASENYA; echo
    [ -n "$CONTRASENYA" ] || error "la contrasenya no pot ser buida"
  fi
  echo "$USUARI:$CONTRASENYA" | chpasswd
  # Una casa buida i seva és el punt de muntatge de la memòria
  find "/home/$USUARI" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null || true
  chown "$USUARI:$USUARI" "/home/$USUARI"; chmod 700 "/home/$USUARI"
fi

# ── Fitxers comuns ────────────────────────────────────────────────────────────
echo "==> Configuració"
mkdir -p "$USUARIS_DIR" "$P/usr/local/sbin" "$(dirname "$LIGHTDM_CONF")"
{
  echo "PERFIL=$PERFIL"
  echo "NAVEGADOR=$NAVEGADOR"
  echo "URL=\"$URL\""
  echo "MENU=$MENU"
  echo "ISARD_URL=\"$ISARD_URL\""
} >"$USUARIS_DIR/$USUARI"
chmod 644 "$USUARIS_DIR/$USUARI"
if [ -n "$CA" ]; then install -m 644 "$CA" "$CONF_DIR/ca.crt"; fi

# Muntatge de la casa en memòria a l'entrada i desmuntatge a la sortida. «required»: si no es pot muntar, no entra
# (millor que deixar entrar amb una casa que conserva el que s'hi gravi).
cat >"$P/usr/local/sbin/examen-pam" <<'EOF'
#!/bin/sh
FITXER="/etc/examen/usuaris.d/$PAM_USER"
[ -f "$FITXER" ] || exit 0
. "$FITXER"
CASA="/home/$PAM_USER"
case "$PAM_TYPE" in
  open_session)
    mountpoint -q "$CASA" && exit 0
    OPCIONS="size=512m,mode=0700,uid=$(id -u "$PAM_USER"),gid=$(id -g "$PAM_USER"),nosuid,nodev"
    [ "$PERFIL" = quiosc ] && OPCIONS="$OPCIONS,noexec"
    mount -t tmpfs -o "$OPCIONS" tmpfs "$CASA" || exit 1
    ;;
  close_session)
    pkill -KILL -u "$PAM_USER" 2>/dev/null
    sleep 1
    umount -l "$CASA" 2>/dev/null
    ;;
esac
exit 0
EOF
chmod 755 "$P/usr/local/sbin/examen-pam"

mkdir -p "$(dirname "$PAM_FITXER")"; touch "$PAM_FITXER"
grep -qF "$MARCA" "$PAM_FITXER" || printf '%s %s\n' "$PAM_LINIA" "$MARCA" >>"$PAM_FITXER"

# El «session-wrapper» original de LightDM es guarda un cop, per poder-lo restaurar i per als altres usuaris
if [ ! -f "$CONF_DIR/wrapper-original" ]; then
  # 1) el que tingui configurat LightDM; 2) si no n'hi ha cap, el que LightDM fa servir per defecte
  #    (lightdm-session); 3) com a últim recurs, /etc/X11/Xsession. Així els altres usuaris continuen
  #    iniciant la sessió exactament igual que abans.
  ORIGINAL=$(lightdm --show-config 2>/dev/null | sed -n 's/.*session-wrapper=\(\/[^ ]*\).*/\1/p' | head -1 || true)
  if [ -z "$ORIGINAL" ] || { [ ! -x "$ORIGINAL" ] && [ -z "$P" ]; }; then
    ORIGINAL=""
    for c in /usr/sbin/lightdm-session /usr/lib/lightdm/lightdm-session /etc/X11/Xsession; do
      if [ -x "$c" ] || [ -n "$P" ]; then ORIGINAL="$c"; break; fi
    done
  fi
  [ -n "$ORIGINAL" ] || error "no trobo el session-wrapper de LightDM"
  echo "Session-wrapper original de LightDM: $ORIGINAL"
  echo "$ORIGINAL" >"$CONF_DIR/wrapper-original"
fi

cat >"$P/usr/local/sbin/examen-session-wrapper" <<'EOF'
#!/bin/sh
# Per als usuaris d'examen de perfil quiosc, la sessió és el navegador; per a tothom, la normal.
U=$(id -un)
if [ -f "/etc/examen/usuaris.d/$U" ]; then
  . "/etc/examen/usuaris.d/$U"
  if [ "$PERFIL" = quiosc ]; then exec /usr/local/sbin/examen-kiosk; fi
fi
exec "$(cat /etc/examen/wrapper-original)" "$@"
EOF
chmod 755 "$P/usr/local/sbin/examen-session-wrapper"

cat >"$LIGHTDM_CONF" <<'EOF'
[Seat:*]
session-wrapper=/usr/local/sbin/examen-session-wrapper
# L'autorització de X no es desa a la casa (que és en memòria i es munta després)
user-authority-in-system-dir=true
# Els usuaris sense shell (nologin) no surten a la llista de la pantalla d'entrada: s'hi entra escrivint-ne el nom
greeter-show-manual-login=true
EOF

if [ "$PERFIL" = quiosc ]; then
cat >"$CONF_DIR/openbox-rc.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!-- Gestor de finestres mínim: cap tecla ni menú; tota finestra a pantalla completa i sense decoració. -->
<openbox_config xmlns="http://openbox.org/3.4/rc">
  <focus><focusNew>yes</focusNew><followMouse>no</followMouse></focus>
  <applications>
    <application class="*"><decor>no</decor><maximized>yes</maximized><fullscreen>yes</fullscreen></application>
    <application class="Zenity">
      <decor>no</decor><maximized>no</maximized><fullscreen>no</fullscreen>
      <position force="yes"><x>center</x><y>center</y></position>
    </application>
  </applications>
  <keyboard></keyboard>
  <mouse>
    <context name="Client">
      <mousebind button="Left" action="Press"><action name="Focus"/></mousebind>
    </context>
  </mouse>
</openbox_config>
EOF

cat >"$P/usr/local/sbin/examen-kiosk" <<'EOF'
#!/bin/sh
# Sessió de quiosc: només el navegador. Si es tanca, la sessió s'acaba i torna la pantalla d'entrada.
. "/etc/examen/usuaris.d/$(id -un)"

# Sense les tecles del servidor X: no es pot passar a una consola (Ctrl+Alt+F1…) ni matar X (Ctrl+Alt+Retrocés)
setxkbmap -option srvrkeys:none 2>/dev/null
xset s off 2>/dev/null; xset -dpms 2>/dev/null; xset s noblank 2>/dev/null
openbox --config-file /etc/examen/openbox-rc.xml &
sleep 1

# Certificat de la CA del centre a la base de dades del navegador (la casa és nova a cada sessió)
confia_ca() {   # $1 = directori de la base de dades NSS
  [ -f /etc/examen/ca.crt ] || return 0
  command -v certutil >/dev/null || return 0
  mkdir -p "$1"
  certutil -N -d "sql:$1" --empty-password 2>/dev/null
  certutil -A -d "sql:$1" -n centre-ca -t "C,," -i /etc/examen/ca.crt 2>/dev/null
}

# Menú d'entrada (opcional). Es mostra un cop per sessió: tancar el navegador acaba la sessió (i buida la casa),
# així el següent alumne no troba res de l'anterior.
DESTI="$URL"
if [ "${MENU:-0}" = 1 ]; then
  while :; do
    if [ -n "$ISARD_URL" ]; then
      OPCIO=$(zenity --list --title="Examen" --text="Què vols fer?" --column="Opció" --width=420 --height=260 \
        "Plataforma d'exàmens" "Isard" "Aturar l'ordinador" 2>/dev/null) || exit 0
    else
      OPCIO=$(zenity --list --title="Examen" --text="Què vols fer?" --column="Opció" --width=420 --height=220 \
        "Plataforma d'exàmens" "Aturar l'ordinador" 2>/dev/null) || exit 0
    fi
    case "$OPCIO" in
      "Plataforma d'exàmens") DESTI="$URL"; break ;;
      "Isard")                DESTI="$ISARD_URL"; break ;;
      "Aturar l'ordinador")
        systemctl poweroff && exit 0
        zenity --error --text="No s'ha pogut aturar l'ordinador. Avisa el professor." 2>/dev/null
        ;;
    esac
  done
fi

case "$NAVEGADOR" in
  firefox)
    PERFIL_FF="$HOME/perfil-firefox"
    mkdir -p "$PERFIL_FF"
    confia_ca "$PERFIL_FF"
    cat >"$PERFIL_FF/user.js" <<PREFS
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.aboutwelcome.enabled", false);
user_pref("browser.startup.homepage", "$DESTI");
user_pref("browser.tabs.warnOnClose", false);
user_pref("devtools.policy.disabled", true);
user_pref("devtools.chrome.enabled", false);
user_pref("network.protocol-handler.external-default", false);
user_pref("network.protocol-handler.expose-all", false);
user_pref("signon.rememberSignons", false);
user_pref("app.update.auto", false);
user_pref("datareporting.policy.dataSubmissionEnabled", false);
user_pref("extensions.autoDisableScopes", 15);
PREFS
    exec firefox --kiosk --no-remote --profile "$PERFIL_FF" "$DESTI"
    ;;
  chrome|chromium)
    BIN=$(command -v google-chrome-stable || command -v google-chrome || command -v chromium || command -v chromium-browser)
    confia_ca "$HOME/.pki/nssdb"
    exec "$BIN" --kiosk --incognito --no-first-run --no-default-browser-check --disable-extensions \
      --disable-sync --password-store=basic --user-data-dir="$HOME/chrome" "$DESTI"
    ;;
esac
EOF
chmod 755 "$P/usr/local/sbin/examen-kiosk"
fi

# ── Llista de comprovació d'integritat i informe d'estat ──────────────────────
# El mateix script el fan servir l'informe (cada 15 min) i la comprovació manual de la guia (apartat 5b), perquè donin el mateix resultat.
cat >"$P/usr/local/sbin/examen-comprova" <<'EOF'
#!/bin/sh
# Llista de comprovació de la configuració d'examen: hash dels fitxers, usuaris, grups i cases. S'executa com a root.
cd / || exit 1
{
  find etc/pam.d/lightdm etc/lightdm etc/polkit-1/rules.d/49-examen.rules etc/examen usr/local/sbin/examen-* \
       etc/systemd/system/examen-informa.service etc/systemd/system/examen-informa.timer \
       etc/cron.deny etc/at.deny etc/sudoers etc/sudoers.d -type f ! -name informe.conf 2>/dev/null | sort | xargs -r sha256sum
  getent passwd examen examen-fitxers
  id -nG examen
  id -nG examen-fitxers
  getent group sudo adm
  # La casa d'un usuari d'examen ha d'estar buida quan ningú hi és (si hi ha una sessió, és en memòria i no es mira)
  for u in examen examen-fitxers; do mountpoint -q "/home/$u" || ls -A "/home/$u"; done
} 2>/dev/null
EOF
chmod 755 "$P/usr/local/sbin/examen-comprova"

if [ -n "$INFORME_TOKEN" ]; then
  echo "==> Informe d'estat a $INFORME_URL"
  ( umask 077
    cat >"$CONF_DIR/informe.conf" <<EOF
BASE_URL="$INFORME_URL"
ISARD_URL="$ISARD_URL"
TOKEN="$INFORME_TOKEN"
EOF
  )
  chmod 600 "$CONF_DIR/informe.conf"

  cat >"$P/usr/local/sbin/examen-informa" <<'EOF'
#!/bin/sh
# Envia a la plataforma l'estat d'aquest ordinador. S'executa com a root (temporitzador de systemd).
. /etc/examen/informe.conf
CERT=""; [ -f /etc/examen/ca.crt ] && CERT="--cacert /etc/examen/ca.crt"
codi() { curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@" 2>/dev/null; }

PLAT=false
case "$(codi $CERT "$BASE_URL/")" in 2*|3*) PLAT=true ;; esac
ISARD=""
if [ -n "$ISARD_URL" ]; then
  ISARD=false
  case "$(codi "$ISARD_URL")" in 2*|3*|401|403) ISARD=true ;; esac
fi
NAV=$( { dpkg-query -W -f='firefox ${Version}\n' firefox 2>/dev/null; dpkg-query -W -f='chrome ${Version}\n' google-chrome-stable 2>/dev/null; } | paste -sd' ' -)
DISC=$(df -Pm / | awk 'NR==2{print $4}')
UPT=$(cut -d. -f1 /proc/uptime)
DINS=$(who | wc -l)
INTEGRITAT=$(/usr/local/sbin/examen-comprova)

# El testimoni va per un fitxer de configuració de curl (-K -) i no a la línia d'ordres, que qualsevol usuari pot veure amb «ps»
printf 'header = "X-Equip-Token: %s"\n' "$TOKEN" | curl -s -o /dev/null --max-time 30 $CERT -K - \
  --data-urlencode "nom=$(hostname)" --data-urlencode "integritat=$INTEGRITAT" \
  --data-urlencode "arribaPlataforma=$PLAT" ${ISARD:+--data-urlencode "arribaIsard=$ISARD"} \
  --data-urlencode "navegador=$NAV" --data-urlencode "discLliureMb=$DISC" \
  --data-urlencode "uptimeSegons=$UPT" --data-urlencode "usuarisDins=$DINS" \
  "$BASE_URL/api/equips/informe"
EOF
  chmod 755 "$P/usr/local/sbin/examen-informa"

  mkdir -p "$P/etc/systemd/system"
  cat >"$P/etc/systemd/system/examen-informa.service" <<'EOF'
[Unit]
Description=Informe d'estat d'aquest ordinador a la plataforma d'exàmens
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/examen-informa
EOF
  cat >"$P/etc/systemd/system/examen-informa.timer" <<'EOF'
[Unit]
Description=Informe d'estat cada 15 minuts

[Timer]
OnBootSec=2min
OnUnitActiveSec=15min
RandomizedDelaySec=60

[Install]
WantedBy=timers.target
EOF
  if [ -z "$P" ]; then
    systemctl daemon-reload
    systemctl enable --now examen-informa.timer
  fi
fi

escriu_llistes

echo
echo "Fet. Usuari «$USUARI» (perfil $PERFIL) preparat."
echo "IMPORTANT: LightDM només llegeix la configuració quan arrenca. REINICIA l'ordinador (sudo reboot)"
echo "abans de provar-ho; sense reiniciar, l'usuari $USUARI no entraria en mode quiosc."
echo "L'usuari $USUARI NO surt a la llista de la pantalla d'entrada: tria l'opció d'escriure l'usuari i posa-hi «$USUARI»."
echo "Després prova-ho ABANS de repetir-ho a la resta d'ordinadors: entra com a $USUARI."
if [ "$PERFIL" = quiosc ]; then
  if [ "$MENU" -eq 1 ]; then echo "Ha de sortir un menú; en triar una opció, el navegador a pantalla completa."
  else echo "Ha de sortir només el navegador a pantalla completa amb $URL."; fi
fi
```

## 7. Notes tècniques i limitacions conegudes

- **Navegador:** les preferències de Firefox d'aquest script (fitxer `user.js`) són una protecció addicional, no una garantia: el que realment
  impedeix sortir és que la sessió no té escriptori, menús ni programes. El Chrome s'inicia en mode quiosc i incògnit amb una casa nova.
- **Perfil escriptori:** conserva els programes de l'ordinador; el que se li treu és la possibilitat de deixar res (casa en memòria),
  d'entrar per consola i d'actuar amb privilegis. Si un exàmen necessita desar fitxers entre dues sessions, no és aquest perfil.
- **Pendrive:** s'impedeix amb la regla de polkit; si hi ha un altre mecanisme de muntatge automàtic a l'ordinador, cal comprovar-ho a la prova.
- **Tecles del servidor X:** `setxkbmap -option srvrkeys:none` només afecta la sessió de quiosc. Les consoles continuen accessibles per als altres usuaris.
- **Contrasenya compartida:** és coneguda per tots els alumnes. Canvia-la quan vulguis tornant a executar l'script. Cap alumne l'ha de poder fer servir per
  res més que per fer l'examen: l'usuari no té permisos, i la seva casa es buida a cada sessió.
- **Dues sessions alhora** amb el mateix usuari d'examen a un mateix ordinador no estan suportades (la casa en memòria és una).

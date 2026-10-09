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
  local llista
  llista=$(usuaris_configurats | sed 's/.*/"&"/' | paste -sd, -)
  if [ -z "$llista" ]; then rm -f "$POLKIT_REGLA"; return; fi
  cat >"$POLKIT_REGLA" <<EOF
// Els usuaris d'examen no poden fer cap acció privilegiada (muntar discs, apagar, canviar xarxa…).
var usuarisExamen = [$llista];
polkit.addRule(function(action, subject) {
  if (usuarisExamen.indexOf(subject.user) >= 0) { return polkit.Result.NO; }
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
    rm -f "$LIGHTDM_CONF" "$P/usr/local/sbin/examen-pam" "$P/usr/local/sbin/examen-session-wrapper" \
          "$P/usr/local/sbin/examen-kiosk" "$CONF_DIR/openbox-rc.xml" "$CONF_DIR/wrapper-original"
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
    openbox x11-xkb-utils x11-xserver-utils libnss3-tools
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

case "$NAVEGADOR" in
  firefox)
    PERFIL_FF="$HOME/perfil-firefox"
    mkdir -p "$PERFIL_FF"
    confia_ca "$PERFIL_FF"
    cat >"$PERFIL_FF/user.js" <<PREFS
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.aboutwelcome.enabled", false);
user_pref("browser.startup.homepage", "$URL");
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
    exec firefox --kiosk --no-remote --profile "$PERFIL_FF" "$URL"
    ;;
  chrome|chromium)
    BIN=$(command -v google-chrome-stable || command -v google-chrome || command -v chromium || command -v chromium-browser)
    confia_ca "$HOME/.pki/nssdb"
    exec "$BIN" --kiosk --incognito --no-first-run --no-default-browser-check --disable-extensions \
      --disable-sync --password-store=basic --user-data-dir="$HOME/chrome" "$URL"
    ;;
esac
EOF
chmod 755 "$P/usr/local/sbin/examen-kiosk"
fi

escriu_llistes

echo
echo "Fet. Usuari «$USUARI» (perfil $PERFIL) preparat."
echo "IMPORTANT: LightDM només llegeix la configuració quan arrenca. REINICIA l'ordinador (sudo reboot)"
echo "abans de provar-ho; sense reiniciar, l'usuari $USUARI no entraria en mode quiosc."
echo "L'usuari $USUARI NO surt a la llista de la pantalla d'entrada: tria l'opció d'escriure l'usuari i posa-hi «$USUARI»."
echo "Després prova-ho ABANS de repetir-ho a la resta d'ordinadors: entra com a $USUARI."
[ "$PERFIL" = quiosc ] && echo "Ha de sortir només el navegador a pantalla completa amb $URL."
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

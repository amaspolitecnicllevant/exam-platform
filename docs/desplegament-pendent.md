# Desplegament pendent: passos exactes

Aquest document porta a la VM tot el que s'ha fet i encara no hi és desplegat. S'ha de seguir **en ordre**. Ha de fer-se **des de la xarxa del centre** (la VM és a `10.100.79.224`) i **fora de l'horari d'examen**.

**Què hi ha pendent:** la restauració d'ordinadors (V38), les mesures de seguretat de sessions i contrasenyes (V39), la visibilitat dels alumnes per al professorat (V40), els límits de peticions a nginx, les dependències i imatges
actualitzades, les alertes de seguretat, la importació de revisions amb IA, el bloqueig de pestanyes, i els scripts nous per als ordinadors de l'aula. El detall és a [funcionalitats.md](funcionalitats.md) i [seguretat.md](seguretat.md).

**Temps:** uns 30-40 minuts per al servidor i 30 més per provar al navegador. L'aplicació **no estarà disponible uns 20-60 segons** quan es reiniciï el backend.

**Qui fa què:** els passos marcats **(tu)** els has de fer tu (clau SSH amb frase de pas, `sudo`, o el navegador). La resta els pot fer Claude per SSH des d'aquest ordinador.

---

## 0. Abans de començar

1. **Comprova que no hi ha cap examen en marxa.** Cap alumne amb una sessió oberta:

   ```
   ssh administrador@10.100.79.224 'docker exec infra-db-1 psql -U examuser examplatform -tAc "select count(*) from exam_sessions where status='"'"'IN_PROGRESS'"'"' and started_at is not null"'
   ```

   Ha de dir `0`. Si no, espera o avisa el professorat.
2. **Anota l'estat actual**, per saber a què es torna si cal desfer-ho:

   ```
   ssh administrador@10.100.79.224 'cd ~/exam-platform && git log --oneline -1; docker exec infra-db-1 psql -U examuser examplatform -tAc "select max(version::int) from flyway_schema_history"'
   ```

   Apunta l'últim commit (és el punt de retorn) i la versió de la BD (hauria de ser **37**; després ha de ser **40**).
3. **Mira quins ordinadors de l'aula estan encesos** si vols provar-ho també a l'aula (apartat 7).

## 1. Còpia de seguretat de la BD

La còpia es fa **abans de tocar res** (les migracions afegeixen columnes i són reversibles, però cal tenir-ne una). Amb `umask 077` perquè el fitxer no sigui llegible per altres usuaris:

```
ssh administrador@10.100.79.224 '(umask 077; mkdir -p ~/backups-manual; docker exec infra-db-1 pg_dump -U examuser -Fc examplatform > ~/backups-manual/abans-V40-$(date +%Y%m%d-%H%M).dump) && ls -la ~/backups-manual | tail -3'
```

Comprova que el fitxer pesa uns centenars de KB (no zero). Tens també la còpia automàtica diària a `~/exam-platform/backups/`.

## 2. Baixar el codi **(tu)**

La clau SSH de la VM per a GitHub té frase de pas, per això ho has de fer tu:

```
ssh -t administrador@10.100.79.224 'cd ~/exam-platform && git pull --ff-only && git log --oneline -1'
```

Ha de dir `Fast-forward` i acabar a l'últim commit del repositori (`git log --oneline -1` en aquest ordinador). Si diu que no pot fer fast-forward, **no continuïs**: avisa'm.

## 3. Variables de configuració

Les noves variables tenen valors per defecte i **no cal tocar res**:

| Variable | Valor | Estat |
|:--|:--|:--|
| `EQUIPS_TOKEN` | testimoni dels informes d'ordinadors | ja és al `.env` de la VM (es va afegir en desplegar V37) |
| `JWT_MAX_SESSION_MS` | `43200000` (12 h) | per defecte |
| `EQUIPS_SENSE_NOTICIES_MIN` | `45` | per defecte |
| `EQUIPS_AVIS_DIES` | `7` | per defecte |

Comprova-ho, i de passada tanca el permís del fitxer (conté secrets):

```
ssh administrador@10.100.79.224 'cd ~/exam-platform/infra && chmod 600 .env && grep -c "^EQUIPS_TOKEN=." .env && ls -l .env'
```

Ha de dir `1` i `-rw-------`.

## 4. Construir i arrencar

Es reconstrueix **sense memòria cau** perquè els `Dockerfile` portin els últims pegats de seguretat del sistema (si no, Docker reutilitza les capes antigues). Triga uns 5-8 minuts.

```
ssh administrador@10.100.79.224 'cd ~/exam-platform/infra && docker compose -f docker-compose.yml -f docker-compose.https.yml build --pull --no-cache backend frontend 2>&1 | tail -3'
ssh administrador@10.100.79.224 'cd ~/exam-platform/infra && docker compose -f docker-compose.yml -f docker-compose.https.yml pull db backup 2>&1 | tail -2 && docker compose -f docker-compose.yml -f docker-compose.https.yml up -d backend frontend backup 2>&1 | tail -6'
```

`backup` s'hi inclou perquè el compose li afegeix el grup 101 (necessari per llegir els fitxers dels alumnes quan es tanquin els permisos). Si `up -d` també volgués recrear `db`, **és normal**: les dades són en un volum.

## 5. Verificacions al servidor

Espera uns 40 segons i comprova, en aquest ordre:

```
ssh administrador@10.100.79.224 'cd ~/exam-platform/infra
echo "--- contenidors:"; docker ps --format "{{.Names}} {{.Status}}"
echo "--- BD (ha de ser 40):"; docker exec infra-db-1 psql -U examuser examplatform -tAc "select max(version::int) from flyway_schema_history"
echo "--- backend (ha de dir Started i cap ERROR):"; docker logs --since 3m infra-backend-1 2>&1 | grep -E "Started|ERROR|Successfully applied" | tail -4
echo "--- web:"; curl -sk -o /dev/null -w "front %{http_code}\n" https://localhost:3443/
echo "--- CSP sense scripts en línia (ha de dir 0):"; curl -sk https://localhost:3443/ | grep -oE "<script[^>]*>" | grep -vc "src="
echo "--- capçalera CSP:"; curl -sk -I https://localhost:3443/ | grep -i content-security | cut -c1-110
echo "--- configuració pública sense llindars (copiesLlindar ha de ser 0):"; curl -sk https://localhost:3443/api/configuracio | grep -o "\"copiesLlindar\":[0-9]*"
echo "--- informe sense testimoni (401) i endpoints protegits (403):"; curl -sk -o /dev/null -w "informe %{http_code}\n" -X POST https://localhost:3443/api/equips/informe -d nom=x; curl -sk -o /dev/null -w "alertes %{http_code}\n" https://localhost:3443/api/admin/alertes'
```

**Si alguna cosa no quadra:** la BD no és a 40, el backend té `ERROR`, o la web no respon → vés a l'apartat «Tornar enrere».

## 6. Proves al navegador **(tu)**

Això és el que **no s'ha pogut comprovar visualment** i és el més important. Obre `https://examens.politecnicllevant.cat:3443` amb **Ctrl+F5** (perquè carregui el frontend nou) i prova, per ordre. Convé una finestra d'incògnit per a l'alumne, per tenir dues sessions alhora.

**Com a administrador**
- [ ] Entres. Si la **barra de seguretat** de dalt surt amb alertes, **és normal** que hi hagi avisos antics de les proves; mira que el text s'entengui i que *Veure-les* desplegui la llista.
- [ ] *Usuaris* → *Tancar sessions* d'un usuari de prova: demana confirmació i diu «Sessions tancades».
- [ ] *Auditoria*: el desplegable té accions noves (`LOGIN_FALLIT`, `LOGIN_BLOQUEJAT`…) i en filtrar-ne una, es veu.
- [ ] *Aules* → *Ordinadors* en una aula: es veu la taula (probablement buida fins que els ordinadors informin).
- [ ] Canvies la teva contrasenya (barra lateral): demana **12 caràcters com a mínim** i després segueixes dins sense tornar a entrar.

**Com a professor**
- [ ] A *Usuaris* i a *Grups* **només veus els alumnes dels teus mòduls**, els dels teus grups i els que has creat. No veus la resta ni altres professors.
- [ ] Crees un alumne de prova: **continua apareixent** a la llista (no «desapareix»).
- [ ] A la llista d'exàmens, un examen amb aula mostra l'**estat dels ordinadors** de l'aula.
- [ ] Menú **Exportar** d'un examen, sobretot de l'últim de la llista: s'obre sencer, sense quedar tapat.
- [ ] Un examen **desactivat** amb alumnes continua mostrant *Correccions*, *Estadístiques* i *Exportar*.
- [ ] *Correccions* → **Importar revisió IA**: enganxes un CSV de prova i veus la previsualització abans d'aplicar res.

**Com a alumne (finestra d'incògnit)**
- [ ] Comences un examen de prova. En obrir una **segona pestanya** de la plataforma, es bloqueja amb un missatge; i el professor veu una pèrdua de focus al Monitor.
- [ ] Prems **Entregar** i **cancel·la**: **no** ha de sumar cap pèrdua de focus al Monitor.
- [ ] Una resposta HTML/CSS es veu a la previsualització (sense executar scripts, és normal).
- [ ] Les notes amb el professor sense publicar: l'alumne no veu res més que «pendent de correcció».

**Si algun d'aquests falla**, anota què veus (captura) i avisa'm abans de continuar: pot ser un detall de presentació que es corregeix sense tornar enrere.

## 7. Ordinadors de l'aula

Els ordinadors que ja tenen els usuaris d'examen (el `10.100.94.19`) funcionen tal com estaven. Per **activar l'estat a l'aplicació i la restauració**, cal tornar a passar-hi l'script (nou, idempotent) amb el testimoni. L'ordre exacta, els passos i la prova són a [usuari-examen-linux.md](usuari-examen-linux.md), apartats 3 i 3b. En resum, des d'aquest ordinador:

1. Extreure l'script de la guia i copiar-lo al `.19` amb `ca.crt` (apartat 3).
2. Executar-lo amb `--tot` i `EXAMEN_INFORME_TOKEN` (el valor de `EQUIPS_TOKEN` del `.env` de la VM: `ssh administrador@10.100.79.224 'grep ^EQUIPS_TOKEN ~/exam-platform/infra/.env'`).
3. Al cap de 2-3 minuts, a *Aules → Ordinadors* ha d'aparèixer el `.19`. Llavors **fixa'l com a referència** (és l'ordinador validat).
4. Prova **Restaurar** sobre el `.19` (cal que l'script s'hagi instal·lat amb `--tot`).
5. Només llavors, repeteix-ho a la resta d'ordinadors (apartat 3b), idealment abans reiniciant-los perquè agafin la configuració de LightDM.

Recorda que a l'aula, els alumnes amb permisos d'administrador poden desfer-ho; vegeu l'apartat 5b d'aquella guia.

## 8. Tancar la VM **(tu, amb `sudo`)**

Això és el que **més redueix el risc** i només es pot fer des de la xarxa del centre.

1. **Permisos** (secrets, còpies i fitxers dels alumnes). Les dues primeres línies no necessiten `sudo`:

   ```
   ssh -t administrador@10.100.79.224 'cd ~/exam-platform && chmod 700 backups ~/backups-manual infra/tls/certs && find backups ~/backups-manual -type f -exec chmod 600 {} + && sudo chmod 750 /opt/exam-files /opt/exam-scripts && cd infra && docker compose -f docker-compose.yml -f docker-compose.https.yml exec backup /backup/fes-copia.sh 2>&1 | tail -4'
   ```

   L'última ordre fa una còpia de prova: ha d'acabar bé (el servei de còpies ha de poder llegir els fitxers amb el grup 101). Si falla, **desfés** el `chmod 750` amb `sudo chmod 755 /opt/exam-files /opt/exam-scripts` i avisa'm.
2. **Limitar la xarxa.** Primer mira què farà, sense canviar res, i comprova que **la teva adreça queda dins del rang**:

   ```
   ssh -t administrador@10.100.79.224 'cd ~/exam-platform/infra && ./restringeix-xarxa.sh --prova'
   ```

   Si el rang per defecte (`10.100.0.0/16`) inclou les xarxes des d'on s'ha d'entrar (aules, professorat i la teva), aplica'l **amb una altra sessió SSH oberta** per si cal desfer-ho:

   ```
   ssh -t administrador@10.100.79.224 'cd ~/exam-platform/infra && sudo ./restringeix-xarxa.sh'
   ```

   Comprova des d'un ordinador de l'aula i des del teu que la web continua carregant. Per desfer-ho: `sudo ./restringeix-xarxa.sh --desfes`.

## 9. Escaneig final

```
ssh administrador@10.100.79.224 'cd ~/exam-platform/infra && ./escaneja-imatges.sh 2>&1 | tail -30'
```

Només s'hi accepta `spring-webmvc` (Java) i les de `gosu` de `postgres:16-alpine`; vegeu [seguretat.md](seguretat.md). Qualsevol altra cosa, avisa'm.

---

## Tornar enrere

Les migracions (V38, V39 i V40) **només afegeixen columnes** amb valor per defecte, de manera que **el codi antic funciona amb la BD nova**. Per tornar al codi anterior no cal restaurar la BD:

```
ssh -t administrador@10.100.79.224 'cd ~/exam-platform && git checkout <commit anotat al pas 0> && cd infra && docker compose -f docker-compose.yml -f docker-compose.https.yml build backend frontend && docker compose -f docker-compose.yml -f docker-compose.https.yml up -d backend frontend'
```

(Quan es vulgui tornar a la versió nova: `git checkout main && git pull --ff-only`.)

Només si les **dades** han quedat malament (cosa que no hauria de passar): primer **verifica** la còpia del pas 1, sense tocar res de producció, i després restaura-la. **Avisa'm abans de restaurar res**, perquè substitueix la BD i atura l'aplicació:

```
cd ~/exam-platform/infra/backup
./restaura.sh --prova ~/backups-manual/abans-V40-AAAAMMDD-HHMM.dump     # restaura en un PostgreSQL temporal i compara
./restaura.sh ~/backups-manual/abans-V40-AAAAMMDD-HHMM.dump             # de debò: fa abans una còpia de l'estat actual
```

## Què canvia per als usuaris

- **Professorat i administració:** les contrasenyes noves han de tenir **12 caràcters** (les existents continuen vàlides fins que es canviïn).
- **Sessions:** duren com a màxim **12 hores** des de l'entrada, encara que s'hagin anat renovant; canviar la contrasenya tanca les altres sessions.
- **Professorat:** ja només veu els alumnes de les seves assignatures; un alumne d'un altre mòdul (convalidació, recuperació) l'ha de matricular l'administració abans que el pugui veure.
- **Alumnes:** durant l'examen només poden tenir una pestanya oberta, i el diàleg de confirmar l'entrega ja no compta com a pèrdua de focus.
- **Administració:** barra d'alertes de seguretat a totes les pàgines.

# Fase 3 — Qualitat, anàlisi i integracions externes

## Estat
**EN CURS** — iniciada 2026-09-29.

- [x] 1.1 Tests de `CorrectionService` (2026-09-29): `CorrectionServiceTest`, 61 tests. Substitueix `CorrectionAutoScoreTest`, que estava trencat (feia stub de `execute` amb 2 arguments).
- [x] Correcció automàtica més justa (2026-09-29): amb `:::output-*` cal exit 0 (error/timeout = 0 punts); `output-contains` buit s'ignora; la regex es talla al mateix fil (sense fils penjats); el parser rebutja `output-contains` buit i `output-regex` invàlida.
- [x] Correcció assistida (2026-09-29): bloc `:::clau` per a preguntes de text, nota proposada amb motius de penalització (text i codi), execució automàtica del codi en entregar (pool `correction.async-threads`), acceptar propostes (una / per alumne / totes), bloqueig de publicar notes amb pendents. Migració V25.
- [x] Deute de tests: tests d'integració reactivats (`mvn test -Pintegration`; `api.version` per a Docker ≥ 25; fixtures amb `rawMd`) i `OAuth2DomainRestrictionTest` arreglat.
- [x] Errors corregits (2026-09-29): `startedAt` ara es desa en tornar a fer un examen (abans l'examen es tancava sol); accés de professors a sessions/respostes/execucions/notes unificat amb `ExamService.assertOwnership` (creador, professors del mòdul o admin). Desplegat (migració V25). `GET /api/exams/{id}` només retorna solucions (model, criteris, conceptes clau, resposta correcta) a qui pot gestionar l'examen; la resta el rep en vista d'alumne.
- [x] Auditoria de seguretat i execució (2026-09-29), tot desplegat: convits exigeixen la contrasenya dels comptes existents; professors només poden crear alumnes; secret JWT i contrasenya de BD rotats i obligatoris (el backend no arrenca amb el secret per defecte); BD i backend només a 127.0.0.1; sandbox Docker endurit (aturada real en timeout, límit de sortida, `--cap-drop=ALL`, `--pids-limit`, usuari `nobody`); l'alumne no executa el `:::test` durant l'examen; rellotge des que l'alumne obre l'examen (V28); IP real via Tomcat RemoteIpValve i aula comprovada a cada desament; termini en desar respostes; permisos als fitxers de dades i descàrregues amb token; límit d'intents de login (401/429); nginx amb 12 MB i capçaleres de seguretat.
- [x] Segona auditoria (2026-09-29), tot desplegat: "tornar a fer" limitat (examen obert, notes no publicades, aula) i sense reiniciar el rellotge; execució Docker fora de transaccions i límit d'execucions interactives simultànies; editor Monaco empaquetat (sense CDN); reprendre sessió amb el rellotge aturat (mínim 10 min); convits i programació amb comprovació de mòdul/grup; auditoria de notes i correccions; Spring Boot 3.5.16; OAuth amb correu verificat; contrasenyes de 8 a 72 caràcters.
- [x] 1.2 Revisió manual millorada (2026-09-29): vista "Corregir per pregunta", filtres (alumne, pregunta, pendent/revisada), correcció amb el teclat (Intro), comentari per resposta visible a l'alumne en publicar notes (V29).
- [x] 1.3 Re-correcció i bonus (2026-09-29): "Anul·lar" passa a ser **Bonus** (tothom té la pregunta bé, també qui no la va respondre o ja estava puntuat); el bonus s'aplica en calcular la nota (regla única a `Puntuacio.java` / `notes.ts`), així que es pot desfer sense perdre notes; auditoria de re-correccions amb valor anterior → nou; CSV amb una fila per pregunta, columna `punts` i fila `TOTAL`. Corregit de pas: la pàgina de resultats de l'alumne sumava la proposta i la nota revisada de la mateixa resposta.
- [x] 2.1 Dashboard d'estadística (2026-09-29): pàgina `/professor/exams/:id/stats` (endpoint `GET /api/exams/{id}/stats`, calculat al backend amb `Puntuacio`): entregats, mitjana, mediana, mínima–màxima, % aprovats, histograma de notes amb llindar d'aprovat; per pregunta rendiment, % d'encerts, en blanc i opcions triades (test), ordenable per dificultat. PDF via impressió del navegador (sense dependències noves). No inclou el temps per pregunta: la plataforma no el registra.
- [x] 2.2 Historial de l'alumne (2026-09-29): pàgina `/student/historial` ("Historial i notes" al menú; endpoint `GET /api/sessions/my/historial`): resum (entregats, amb nota, mitjana, aprovats) i, per mòdul, la mitjana, un gràfic d'evolució de les notes publicades i la llista d'exàmens amb nota o "pendent de correcció". La nota només s'envia si el professor l'ha publicada.
- [x] 2.3 Detecció d'anomalies (2026-09-30):
  - Monitor i correcció fan servir el llindar de pèrdues de focus configurat (abans, 3 fix al codi); avís al monitor amb els alumnes que hi arriben.
  - Informe "Possibles còpies" (`GET /api/exams/{id}/copies`, a la correcció): parells d'alumnes amb respostes semblants a TEXT/LONG, SHORT (≥ 8 paraules), scripts, Java i HTML (fragments de 3 tokens, codi sense comentaris i amb variables renombrades; es descompta enunciat, resposta model i el que comparteixen ≥ 5 respostes i el 40 % de la classe), i 3+ errades de test amb la mateixa opció incorrecta. BASH_CMD/PS_CMD no es comparen. Llindar per centre (`copies_llindar`, 80 % per defecte, V30). Només informe: sense avisos automàtics; consultar-lo queda a l'auditoria.
  - Preguntes **amb apunts** (en paper): etiqueta `[apunts]` a la secció o a la pregunta, o casella a la previsualització (V31). L'informe hi aplica un llindar propi (`copies_llindar_apunts`, 95 % per defecte, configurable per centre), les etiqueta "📖 amb apunts" i les fa pesar menys a l'ordre de sospita. L'alumne veu "📖 Pots fer servir apunts" a la pregunta.
- [x] 3.3 Còpies de seguretat (2026-09-30): servei `backup` al docker-compose (`infra/backup/`): cada dia a `BACKUP_HORA` (02:30) `pg_dump` comprimit i verificat + `.tar.gz` de `/opt/exam-files`, amb SHA-256, retenció `BACKUP_RETENCIO_DIES` (30) i permisos 600 a `BACKUP_DIR` (`backups/`). `restaura.sh --prova` restaura en un PostgreSQL temporal i compara files; `restaura.sh` restaura de debò (fa abans una còpia de l'estat actual). Estat a Configuració i barra d'avís per als administradors si falla o fa > 26 h. Còpia a una carpeta compartida SMB preparada (`docker-compose.copies-remotes.yml`), **pendent de configurar** amb les dades del centre: vegeu `docs/copies-seguretat.md`.
- [x] 4.1 Descoberta d'exàmens publicats manualment: ja resolt (opció A). `findPublishedForStudent` inclou els exàmens publicats sense programar dels mòduls on l'alumne està matriculat.
- [x] Millores d'experiència (2026-09-30):
  - Alumne: barra fixa a dalt amb el temps restant (vermell els últims 5 minuts) i l'estat del desament ("Canvis sense desar…", "Desant…", "✓ Respostes desades" o un error); avisos quan queden 10 minuts i 1 minut.
  - Professor: cerca per títol (sense distingir accents) i filtres per estat i mòdul a la llista d'exàmens.
  - Professor: **Duplicar** un examen (`POST /api/exams/{id}/duplicate`): esborrany nou "Còpia de …" amb les preguntes (també les modificades des de la web), la configuració i els fitxers de dades (copiats a la carpeta de la pregunta nova). No es copien la programació, les notes visibles ni els bonus. La còpia és de qui la fa. Queda a l'auditoria (`EXAM_DUPLICATED`).
- [x] Recuperacions (2026-09-30):
  - Editar el **títol** (sempre) i la **durada** (només en esborrany, 1–480 min, comprovant solapaments si està programat) a Previsualitzar (`PATCH /api/exams/{id}/settings`). Queda a l'auditoria (`EXAM_RENAMED`, `EXAM_DURADA`).
  - Un examen programat per a un grup només el poden començar els alumnes del grup: abans, qualsevol alumne matriculat al mòdul hi podia entrar amb l'enllaç directe.
  - "Crear grup amb els suspesos" a Estadístiques (`GET /api/exams/{id}/recuperacio`, `POST /api/exams/{id}/recuperacio/grup`): llista els suspesos (nota sobre 10 < 5, marcant les provisionals) i els no presentats; es trien els alumnes i es crea un grup del mateix mòdul. Només s'hi poden posar alumnes que tenen sessió a l'examen. Queda a l'auditoria (`GRUP_RECUPERACIO`).
  - Flux: crear el grup → Duplicar l'examen → canviar-li el títol → Programar-lo per al grup.
- [x] Tercera auditoria (2026-09-30), tot desplegat:
  - **Hores**: l'hora de programació es desava com si fos UTC (l'examen s'activava 1–2 h tard) i el monitor sumava 2 h. Ara la JVM és UTC, l'API envia i rep instants amb zona ("…Z") i els missatges mostren l'hora local (`app.zona-horaria`, Europe/Madrid). Migració V32: corregeix les hores programades ja desades.
  - **Entrega**: abans d'entregar (a mà o en acabar el temps) s'envien els canvis pendents i s'esperen els desaments en curs; el servidor espera 20 s (`exam.auto-submit-grace-seconds`) abans d'entregar d'ofici. Compte enrere calculat des de l'hora de final.
  - **Tancar/Desactivar**: tancar entrega les sessions en curs (també el tancament automàtic, que ara també tanca quan tothom ha entregat); amb l'examen tancat o desactivat no es pot desar, començar, executar ni reprendre.
  - **Activació programada**: ja no es bloqueja (sense avís) per un altre examen publicat de qualsevol mòdul; només hi ha conflicte per solapament entre exàmens programats del grup. Ja no cal tancar l'original abans de programar una recuperació.
  - Canviar la penalització torna a corregir el test entregat. "Reobrir accés" només per a exàmens programats. Errors de petició → 4xx (abans 500 amb traça). Convits: un sol missatge per a contrasenya incorrecta o compte de Google. Eliminar usuaris amb dades → missatge clar; un admin no es pot eliminar a si mateix. CSV: màxim real a TOTAL i noms sense fórmules. Assignar examen a grup: matrícula al mòdul de qualsevol curs. Token renovat automàticament quan li queda < 1 h (`POST /api/auth/refresh`). Correus en minúscules (V33, índex únic). Límits de llargada en crear comptes i validació del CSV d'alumnes. La resposta correcta del test ha de ser una de les opcions. CSP a nginx.
  - Error trobat en verificar: les preguntes de test mostraven també un quadre de text lliure que podia substituir la lletra triada (cap resposta real afectada).
  - HTTPS preparat amb una autoritat de certificació pròpia del centre (accés per IP): `infra/tls/genera-certificats.sh`, `infra/docker-compose.https.yml`, port 3443 i el 3000 redirigeix. **Pendent d'activar**: vegeu `docs/https.md`.
- [x] Alta massiva d'alumnes (2026-09-30): importació CSV amb matrícula al mòdul, grup (es crea si no existeix) i contrasenyes generades (`ImportacioUsuarisService`); matrícula en bloc a Matrícules (`POST /api/matricules/lot`). El link de convit continua sent l'opció perquè cada alumne es doni d'alta sol.
- [x] Notes sobre 10 (2026-09-30): estadístiques, historial de l'alumne i recuperació passen la nota a base 10 (`Puntuacio.notaSobreDeu`), perquè abans donaven per fet que l'examen valia 10 punts. La correcció i els resultats de l'alumne mostren els punts sobre el màxim real i, si l'examen no val 10, també la nota sobre 10. L'exportació CSV continua en punts.
- [x] 4.2 Filtres per mòdul i curs a `ConvitsPage` (2026-09-30), al client (la llista ja es carrega sencera); s'apliquen als links actius i a l'historial.

---

## Motivació

La Fase 2 ha consolidat el model de dades complet i tots els fluxos bàsics. La Fase 3 té tres eixos:

1. **Qualitat de la correcció** — eines per al professor un cop l'examen és tancat.
2. **Anàlisi i estadística** — visibilitat sobre el rendiment dels alumnes i la dificultat de les preguntes.
3. **Integracions externes opcionals** — Google Classroom i CI/CD, tots dos amb degradació elegant.

---

## Eix 1 — Qualitat de la correcció

### 1.1 Tests unitaris de `CorrectionService` (deute tècnic de F2)
El scoring per `output-contains`, `output-exact`, `output-regex` i `test-script` no té tests unitaris propis.
- Afegir `CorrectionServiceTest` amb casos: sortida correcta, sortida incorrecta, timeout, script que falla.
- Prioritat alta: és lògica crítica sense cobertura directa.

### 1.2 Revisió manual millorada (`CorrectionPage`)
Ara el professor veu totes les respostes en una llista plana. Millorar:
- Vista agrupada per pregunta (veure totes les respostes d'una mateixa pregunta juntes → facilita calibratge).
- Filtre per alumne / per pregunta / per corregit/pendent.
- Puntuació ràpida amb teclat (Tab + número) per a correccions massives.
- Afegir comentari per resposta (camp `feedback` a `Answer`).

### 1.3 Re-correcció global
Quan el professor canvia la resposta correcta d'una `CHOICE`, ara s'activa `reCorrectQuestion`. Estendre:
- Poder marcar una pregunta com a "bonus" (tothom rep els punts sense penalització).
- Log de re-correccions: registrar a `audit_logs` cada canvi de `correctChoice` o `anulada`.

---

## Eix 2 — Anàlisi i estadística

### 2.1 Dashboard del professor
Nova pàgina `/professor/stats` o extensió de `CorrectionPage`:
- **Per examen**: distribució de notes (histograma), mitjana, mediana, % aprovats.
- **Per pregunta**: % de respostes correctes, opcions més triades (CHOICE), temps mitjà de resposta.
- Exportació a CSV (ja existeix) + exportació a PDF (via generació al backend amb iText o similar).

### 2.2 Historial de l'alumne
Nova secció a `ExamResultsPage` o pàgina nova `/student/historial`:
- Llistar tots els exàmens fets amb nota i data.
- Evolució temporal de les notes per mòdul.

### 2.3 Detecció d'anomalies (opcional)
- Alertes visuals al monitor si un alumne supera N pèrdues de focus configurable.
- Detecció de respostes idèntiques entre alumnes (possible còpia) — comparació post-examen.

---

## Eix 3 — Integracions externes

### 3.1 Google Classroom (opcional, degradació elegant)
Publicar una nota a Classroom un cop l'examen és tancat i qualificat.
- Nou adaptador `ClassroomAdapter` que implementa una interfície `GradePublisher`.
- Configuració via variables d'entorn: si `GOOGLE_CLASSROOM_ENABLED=false` (per defecte), s'ignora silenciosament.
- OAuth2 per usuari (no de domini): cada professor autoritza individualment.
- **Fora d'abast**: no crear tasques ni assignaments a Classroom des de SEDEX.

### 3.2 CI/CD
Pipeline mínim per a desplegament continu:
- GitHub Actions (o GitLab CI): `mvn test` + `docker build` en cada push a `main`.
- Etapa de qualitat: Spotless (format), test report com a artefacte.
- Desplegament automàtic al servidor de producció via SSH + `docker compose pull && up -d`.
- Secrets gestionats via GitHub Secrets / variables d'entorn del servidor.

### 3.3 Còpies de seguretat automatitzades
- Script `pg_dump` diari amb retenció de 30 dies.
- Opcionalment: còpia a Google Drive personal del professor/admin.

---

## Eix 4 — UX i accessibilitat (millores opcionals de F2)

### 4.1 Descoberta d'exàmens publicats manualment
Quan un professor publica sense programar, l'alumne no veu l'examen fins a l'assignació explícita de grup.
- Opció A: mostrar a l'alumne exàmens publicats del seu mòdul encara que no tingui sessió (canvi a `findPublishedForStudent`).
- Opció B: mantenir el flux actual però millorar la UX del professor ("Publicar i assignar a grup" en un sol pas).
- ✅ Resolt amb l'opció A (vegeu l'estat).

### 4.2 Filtre per mòdul/curs a `ConvitsPage`
L'endpoint `GET /api/invitacions?modulId=X&curs=Y` existeix però la UI no l'usa.
- Afegir selectors de mòdul i curs a la capçalera de `ConvitsPage`.

### 4.3 Notificacions
- Quan un examen s'activa automàticament (scheduler), enviar un avís a l'alumne per email o web push.
- Requereix configuració SMTP (o servei extern) i consentiment de l'usuari.
- **Blocker tècnic**: no hi ha SMTP configurat. Decidir si s'afegeix o s'usa un servei extern (Mailgun, Resend).

---

## Decisions pendents (cal resoldre abans de començar)

1. **Prioritat dels eixos**: els tests de CorrectionService (1.1) i el dashboard (2.1) són independents i es poden fer en paral·lel. Classroom (3.1) i notificacions (4.3) requereixen decisions externes.
2. **Exportació PDF**: iText (llicència AGPL) vs OpenPDF (fork LGPL) vs generar HTML+CSS i convertir amb wkhtmltopdf.
3. **Detecció de còpies (2.3)**: decidir el llindar de similitud i si cal notificació automàtica o només informe.
4. **Notificacions (4.3)**: email SMTP propi vs servei extern vs web push vs cap.

---

## Estimació

| Eix | Esforç estimat |
|-----|---------------|
| 1.1 Tests CorrectionService | 0.5 dies |
| 1.2–1.3 Correcció millorada | 2–3 dies |
| 2.1 Dashboard estadística | 3–4 dies |
| 2.2–2.3 Historial + anomalies | 2 dies |
| 3.1 Classroom | 4–5 dies |
| 3.2 CI/CD | 1–2 dies |
| 3.3 Backups | 0.5 dies |
| 4.x UX/notificacions | 2–3 dies |
| **Total** | **~3 setmanes** |

---

## Ordre recomanat d'implementació

1. **1.1** — Tests CorrectionService (deute tècnic, poc cost, alt valor)
2. **3.2** — CI/CD (habilita desplegament segur per a tot el que ve)
3. **1.2** — Correcció millorada (valor directe per al professor)
4. **2.1** — Dashboard estadística (valor directe per al professor)
5. **4.1** — Descoberta d'exàmens (UX, decisió pendent)
6. **3.1** — Classroom (opcional, depèn del context del centre)
7. La resta segons prioritat del producte

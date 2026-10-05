# SEDEX — Especificació Tècnica Exhaustiva
**Estat del codi: setembre 2026**

> Aquest document descriu **el que existeix implementat** avui. No és una llista de desitjos ni una planificació; és un mirall del codi font. Si hi ha discrepàncies entre aquest document i el codi, el codi mana.

---

## 1. Visió general

**SEDEX** ("Sistema d'Entorn de Desenvolupament d'Exàmens") és una plataforma web per a la gestió i realització d'exàmens en línia, orientada a centres de Formació Professional.

**Funcionalitats principals:**
- Els professors creen exàmens a partir de fitxers Markdown, els gestionen i els programen.
- Els alumnes fan els exàmens en mode controlat (comptador de temps, detecció de pèrdua de focus).
- L'autocorrecció és automàtica per a preguntes d'opció múltiple i de scripts (per sortida o per test).
- La correcció manual és possible per a preguntes obertes.
- L'execució de codi es fa en contenidors Docker aïllats.
- Les invitacions permeten l'autoregistre d'alumnes sense intervenció de l'administrador.

**Stack tècnic:**
| Capa | Tecnologia |
|------|-----------|
| Backend | Java 21, Spring Boot 3.x, JPA/Hibernate, Flyway, PostgreSQL |
| Frontend | React 18, TypeScript, Vite, TailwindCSS, Monaco Editor |
| Infraestructura | Docker Compose (backend + PostgreSQL + frontend Nginx) |
| Autenticació | JWT (HMAC-SHA) + OAuth2 Google (opcional) |
| Execució de codi | Docker sandbox (contenidors efímers) |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL real) |

---

## 2. Model de dades (PostgreSQL, 22 migracions Flyway)

### 2.1 Usuaris i rols

#### Taula `users`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | `gen_random_uuid()` |
| `name` | VARCHAR(255) | NOT NULL |
| `email` | VARCHAR(255) | UNIQUE NOT NULL |
| `password_hash` | VARCHAR(255) | NULL per a usuaris OAuth |
| `oauth_provider` | VARCHAR(50) | ex. `"google"` |
| `oauth_subject` | VARCHAR(255) | ID del proveïdor OAuth |
| `enabled` | BOOLEAN | DEFAULT TRUE |
| `role` | VARCHAR(50) | `ADMIN` / `PROFESSOR` / `STUDENT` |
| `created_at` | TIMESTAMP | |

Índex únic parcial: `(oauth_provider, oauth_subject) WHERE oauth_provider IS NOT NULL`.

#### Enum `Role`
```
ADMIN | PROFESSOR | STUDENT
```

---

### 2.2 Exàmens i preguntes

#### Taula `exams`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `title` | VARCHAR(255) | |
| `durada` | INTEGER | en minuts |
| `instruccions` | TEXT | opcional |
| `status` | VARCHAR(50) | `DRAFT` / `PUBLISHED` / `CLOSED` (DEFAULT DRAFT) |
| `created_by` | UUID FK→users | |
| `raw_md` | TEXT | Markdown original pujat |
| `created_at` | TIMESTAMP | |
| `scheduled_at` | TIMESTAMP | NULL si no programat |
| `scheduled_grup_id` | UUID FK→grups | NULL si no programat |
| `modul_id` | UUID FK→moduls | NULL; filtra visibilitat per alumnes matriculats |
| `aula_id` | UUID FK→aules | NULL; habilita restricció per IP/CIDR |
| `penalitzacio_choice` | NUMERIC(5,4) | Factor penalització CHOICE, DEFAULT 0 |

#### Enum `ExamStatus`
```
DRAFT → PUBLISHED → CLOSED
         ↓
      (unpublish)→ DRAFT
```

#### Taula `questions`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `exam_id` | UUID FK→exams CASCADE | |
| `ordre` | INTEGER | UNIQUE(exam_id, ordre) |
| `tipus` | VARCHAR(50) | vegeu QuestionType |
| `enunciat` | TEXT | |
| `punts` | NUMERIC(4,2) | Suma total de l'examen = 10 obligatòriament |
| `model_resposta` | TEXT | resposta model (professor) |
| `output_contains` | TEXT | criteri: l'output ha de contenir |
| `output_exact` | TEXT | criteri: output coincidència exacta (case-insensitive) |
| `output_regex` | TEXT | criteri: l'output fa match (2 s timeout anti-ReDoS) |
| `test_script` | TEXT | script de comprovació (exit 0 = correcte) |
| `choices` | TEXT | opcions separades per `\n` (per a CHOICE) |
| `correct_choice` | VARCHAR(10) | lletra `a/b/c/d` (per a CHOICE) |
| `anulada` | BOOLEAN | DEFAULT FALSE; si true, tots reben punts màxims |

#### Enum `QuestionType`
| Valor | Categoria | Executable |
|-------|-----------|-----------|
| `TEXT` | Oberta llarga | No |
| `SHORT` | Oberta curta | No |
| `LONG` | Oberta llarga | No |
| `CHOICE` | Opció múltiple | No |
| `BASH_CMD` | Comanda bash | Sí |
| `PS_CMD` | Comanda PowerShell | Sí |
| `BASH_SCRIPT` | Script bash | Sí |
| `PS_SCRIPT` | Script PowerShell | Sí |
| `JAVA_PROG` | Programa Java | Sí |
| `HTML_CSS` | Pàgina web | Sí |
| `SECTION` | Separador de secció | N/A (no puntua) |

Mètodes helpers: `isExecutable()`, `isBash()`, `isScript()`, `isJava()`.

---

### 2.3 Sessions i respostes

#### Taula `exam_sessions`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `exam_id` | UUID FK→exams | UNIQUE(exam_id, student_id) |
| `student_id` | UUID FK→users | |
| `started_at` | TIMESTAMP | |
| `submitted_at` | TIMESTAMP | NULL si en curs |
| `status` | VARCHAR(50) | `IN_PROGRESS` / `SUBMITTED` (DEFAULT IN_PROGRESS) |
| `focus_loss_count` | INTEGER | DEFAULT 0 |
| `client_ip` | VARCHAR(45) | IP capturada en iniciar sessió |

#### Taula `answers`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `session_id` | UUID FK→exam_sessions CASCADE | UNIQUE(session_id, question_id) |
| `question_id` | UUID FK→questions | |
| `contingut` | TEXT | resposta de l'alumne |
| `execution_output` | TEXT | output de l'última execució exitosa |
| `auto_score` | NUMERIC(4,2) | calculat automàticament |
| `manual_score` | NUMERIC(4,2) | establert pel professor |
| `corrected_at` | TIMESTAMP | |

#### Taula `executions`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `answer_id` | UUID FK→answers CASCADE | |
| `executed_at` | TIMESTAMP | |
| `output` | TEXT | |
| `exit_code` | INTEGER | |
| `duration_ms` | BIGINT | |

---

### 2.4 Estructura acadèmica

#### Taula `departaments`
| `id` UUID PK | `nom` VARCHAR(120) UNIQUE | `created_at` |

#### Taula `cicles`
| `id` UUID PK | `codi` VARCHAR(20) UNIQUE | `nom` VARCHAR(120) | `departament_id` FK→departaments CASCADE | `created_at` |

#### Taula `moduls`
| `id` UUID PK | `codi` VARCHAR(20) UNIQUE | `nom` VARCHAR(120) | `cicle_id` FK→cicles CASCADE | `created_at` |

#### Taula `imparticions`
| `id` UUID PK | `professor_id` FK→users CASCADE | `modul_id` FK CASCADE | `curs` VARCHAR(10) | UNIQUE(professor_id, modul_id, curs) |

Significat: quin professor imparteix quin mòdul en quin curs acadèmic.

#### Taula `matricules`
| `id` UUID PK | `alumne_id` FK→users CASCADE | `modul_id` FK CASCADE | `curs` VARCHAR(10) | UNIQUE(alumne_id, modul_id, curs) |

Significat: quin alumne està matriculat en quin mòdul i curs.

#### Taula `professor_departaments`
| `professor_id` FK→users CASCADE | `departament_id` FK CASCADE | `es_cap` BOOLEAN DEFAULT FALSE |
PK(professor_id, departament_id). `es_cap = true` indica cap de departament.

---

### 2.5 Grups

#### Taula `grups`
| `id` UUID PK | `name` VARCHAR(120) | `created_by` FK→users | `modul_id` FK→moduls (nullable, SET NULL) | `created_at` |

#### Taula `grup_students`
| `grup_id` FK→grups CASCADE | `student_id` FK→users CASCADE | PK(grup_id, student_id) |

---

### 2.6 Invitacions

#### Taula `invitacions`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `token` | UUID UNIQUE | generat automàticament |
| `modul_id` | UUID FK→moduls CASCADE | |
| `grup_id` | UUID FK→grups SET NULL | opcional |
| `curs` | VARCHAR(10) | |
| `created_by` | UUID FK→users CASCADE | |
| `expires_at` | TIMESTAMP | 7 dies des de la creació |
| `uses_count` | INTEGER | DEFAULT 0 |
| `max_uses` | INTEGER | NULL = il·limitat |
| `active` | BOOLEAN | DEFAULT TRUE |
| `created_at` | TIMESTAMP | |

Mètode `isValida()`: `active && !expired && (maxUses==null || usesCount < maxUses)`.

---

### 2.7 Aules (restricció de xarxa)

#### Taula `aules`
| `id` UUID PK | `nom` VARCHAR(100) UNIQUE | `xarxa_cidr` VARCHAR(50) | `created_at` |

Les aules defineixen un rang CIDR. Si un examen té aula assignada, l'alumne ha d'accedir des d'una IP dins d'aquell rang.

---

### 2.8 Fitxers de pregunta

#### Taula `question_files`
| Columna | Tipus | Notes |
|---------|-------|-------|
| `id` | UUID PK | |
| `question_id` | UUID FK→questions CASCADE | |
| `filename` | VARCHAR(255) | nom original (sanititzat) |
| `stored_path` | VARCHAR(500) | ruta al servidor |
| `content_type` | VARCHAR(100) | MIME type |
| `file_size` | BIGINT | mida en bytes |
| `created_at` | TIMESTAMP | |

Els fitxers es desen a `execution.files-host-path/questions/{questionId}/`. Durant l'execució es munten a `/data/files/` dins del contenidor.

---

### 2.9 Configuració del sistema

#### Taula `configuracio_sistema` (fila única, `id = 1`)
| Columna | Defecte | Notes |
|---------|---------|-------|
| `nom_centre` | `"SEDEX"` | Nom del centre |
| `logo_base64` | NULL | Logo en Base64 |
| `logo_mime` | NULL | MIME del logo |
| `color_marca` | `"#8b1a4a"` | Color corporatiu (hex) |
| `curs_actiu` | `"2026-27"` | Curs acadèmic actiu per defecte |
| `durada_defecte` | `90` | Durada d'examen en minuts |
| `penalitzacio_defecte` | `0` | Factor penalització CHOICE (0–1) |
| `focus_loss_threshold` | `5` | Nombre de pèrdues de focus que activa alerta |
| `grace_period_seconds` | `0` | Gràcia extra (s) entre fin del temps i tancament |
| `dominis_oauth` | `"politecnicllevant.cat"` | Dominis permesos per OAuth2 (comes) |

---

### 2.10 Auditoria

#### Taula `audit_log`
| `id` UUID PK | `user_id` FK→users SET NULL | `action` VARCHAR(100) | `resource` VARCHAR(255) | `ip_address` VARCHAR(45) | `created_at` |

Accions registrades: `EXAM_CREATED`, `EXAM_PUBLISHED`, `EXAM_CLOSED`, `EXAM_DELETED`, `EXAM_SUBMITTED`, `USER_CREATED`, `USER_DELETED`, `PASSWORD_RESET`.

---

## 3. Regles de negoci i lògica de domini

### 3.1 Cicle de vida d'un examen

```
DRAFT ──publish──► PUBLISHED ──close──► CLOSED
  ▲                    │
  └──── unpublish ─────┘
```

- **DRAFT**: editable, assignable a mòdul/aula, programmable, eliminable.
- **PUBLISHED**: visible per als alumnes. No eliminable. Pot tenir sessions actives.
- **CLOSED**: tancat definitivament. No és possible cap transició. Exportable com a CSV.

### 3.2 Programació automàtica

Un examen DRAFT pot tenir `scheduledAt` (data/hora futura) i `scheduledGrup`. El servei `ExamSchedulerService` gestiona tres tasques:

| Tasca | Freqüència | Lògica |
|-------|-----------|--------|
| `activateScheduled` | 60 s | Si `now >= scheduledAt` i l'examen és DRAFT → el publica i crea sessions per als alumnes del grup |
| `closeExpiredExams` | 60 s | Si `now >= scheduledAt + durada + autoCloseGraceMinutes` o tots els alumnes han entregat → tanca l'examen |
| `closeExpiredSessions` | 30 s | Si la sessió porta més de `durada` minuts activa → `forceSubmit` |

Config: `exam.access-window-minutes` (defecte 20), `exam.auto-close-grace-minutes` (defecte 5).

**Validació de conflictes** en programar: no pot haver-hi solapament temporal amb cap altre examen del mateix grup que estigui publicat o programat.

### 3.3 Control d'accés a sessions

Quan un alumne inicia sessió (`POST /sessions/start/{examId}`), el sistema verifica per ordre:

1. **Examen publicat**: retorna 403 si no és `PUBLISHED`.
2. **Finestra d'accés**: si `scheduledAt != null`, l'alumne ha d'accedir entre `scheduledAt` i `scheduledAt + accessWindowMinutes`. El professor pot reobrir (establir `scheduledAt = now`).
3. **Restricció d'aula**: si `exam.aulaId != null`, la IP del client (extreta de `X-Forwarded-For` o `remoteAddr`) ha d'estar dins del rang CIDR de l'aula.
4. **Matrícula**: si `exam.modulId != null`, l'alumne ha d'estar matriculat al mòdul i al curs actiu.

Si existeix sessió prèvia per a l'alumne i l'examen (UNIQUE exam_id + student_id), es reprèn la sessió existent sense crear-ne una de nova.

### 3.4 Autocorrecció

**Preguntes CHOICE** (en fer submit i en re-corregir):
- Resposta correcta: `punts`
- Resposta incorrecta: `−punts × penalitzacioChoice` (mai negatiu total)
- Sense resposta: `0` (mai penalitza)
- Pregunta anulada: `punts` per a tothom

**Preguntes executables** (en executar codi, per ordre de prioritat):
1. Si hi ha `testScript`: exit 0 → punts màxims; altrament → 0.
2. Si hi ha `outputExact`: coincidència exacta (case-insensitive) → punts màxims; altrament → 0.
3. Si hi ha `outputContains`: totes les línies del criteri han d'aparèixer → punts màxims; altrament → 0.
4. Si hi ha `outputRegex`: l'output ha de fer match (timeout 2 s) → punts màxims; altrament → 0.
5. Sense criteri: exit 0 → punts màxims.

**Re-correcció en cadena**: quan el professor modifica `correctChoice` o canvia `anulada`, totes les respostes existents de la pregunta es re-corregeixen automàticament.

### 3.5 Punts totals d'un examen

El parser valida que la suma de punts de totes les preguntes (excloent `SECTION`) sigui exactament **10.00**. Si no, el backend retorna error amb el total calculat.

### 3.6 Detecció de pèrdua de focus

El frontend escolta `visibilitychange` i `window.blur` (debounce 50 ms per ignorar focus a iframes pròpies). Mínim 1 s entre events. Cada pèrdua s'envia a `POST /sessions/{id}/focus-loss`. El comptador s'incrementa al servidor. És visible al monitor i a les correccions (groc ≥1, vermell ≥ `focusLossThreshold`).

### 3.7 Sistema d'invitacions

Un professor genera un link (token UUID) associat a un mòdul + curs (opcionalment, un grup). El link és vàlid 7 dies. Pot tenir `maxUses` (NULL = il·limitat).

Quan un alumne obre el link:
- **No autenticat**: pot omplir el formulari de registre → es crea usuari STUDENT → es matricula al mòdul+curs → s'afegeix al grup si n'hi ha → rep JWT i entra directament.
- **Ja autenticat com a STUDENT**: pot clicar "Unir-me" → es matricula i s'afegeix al grup si cal.

En ambdós casos, `usesCount` s'incrementa. El professor pot desactivar el link en qualsevol moment.

---

## 4. API REST

Base path: `/api`. Tots els endpoints retornen JSON tret que s'especifiqui. Errors: missatge en format `{ "error": "...", "message": "..." }` via `GlobalExceptionHandler`.

### 4.1 Autenticació

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `POST` | `/auth/login` | Pública | Email+password → `{ token, userId, name, email, role }` |

OAuth2: `GET /oauth2/authorization/google` → Google → `OAuth2SuccessHandler` → redirect a `{frontendUrl}/oauth-callback#token=...&name=...&email=...&role=...&userId=...`.

JWT: HMAC-SHA, secret configurable (`jwt.secret`), expiració configurable (`jwt.expiration-ms`). Transport: `Authorization: Bearer {token}`.

### 4.2 Exàmens

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `POST` | `/exams` | PROFESSOR/ADMIN | Crea examen des de fitxer `.md` (multipart `file`) |
| `GET` | `/exams/mine` | PROFESSOR/ADMIN | Llista exàmens del professor autenticat |
| `GET` | `/exams/published` | STUDENT | Llista exàmens publicats visibles per l'alumne |
| `GET` | `/exams/{id}` | Qualsevol auth | Detall; professor veu `correctChoice`/`modelResposta` |
| `POST` | `/exams/{id}/publish` | PROFESSOR/ADMIN | DRAFT→PUBLISHED. Audit: `EXAM_PUBLISHED` |
| `POST` | `/exams/{id}/unpublish` | PROFESSOR/ADMIN | PUBLISHED→DRAFT |
| `POST` | `/exams/{id}/close` | PROFESSOR/ADMIN | →CLOSED. Audit: `EXAM_CLOSED` |
| `DELETE` | `/exams/{id}` | PROFESSOR/ADMIN | Elimina (solo DRAFT). Audit: `EXAM_DELETED` |
| `POST` | `/exams/{id}/schedule` | PROFESSOR/ADMIN | Programa `{ scheduledAt, grupId }` |
| `DELETE` | `/exams/{id}/schedule` | PROFESSOR/ADMIN | Elimina programació |
| `POST` | `/exams/{id}/reopen-window` | PROFESSOR/ADMIN | `scheduledAt = now` |
| `PATCH` | `/exams/{id}/modul/{modulId}` | PROFESSOR/ADMIN | Assigna mòdul (valida impartició) |
| `PATCH` | `/exams/{id}/aula/{aulaId}` | PROFESSOR/ADMIN | Assigna aula |
| `DELETE` | `/exams/{id}/aula` | PROFESSOR/ADMIN | Elimina restricció d'aula |
| `PATCH` | `/exams/{examId}/questions/{questionId}` | PROFESSOR/ADMIN | Modifica `correctChoice` / `anulada` → re-corregeix |
| `PATCH` | `/exams/{id}/settings` | PROFESSOR/ADMIN | Actualitza `penalitzacioChoice` (0..1) |

### 4.3 Sessions

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `POST` | `/sessions/start/{examId}` | STUDENT | Inicia/reprèn sessió |
| `PUT` | `/sessions/{id}/answers` | STUDENT | Desa/actualitza una resposta |
| `POST` | `/sessions/{id}/focus-loss` | STUDENT | Registra pèrdua de focus |
| `POST` | `/sessions/{id}/submit` | STUDENT | Entrega. Autocorregeix CHOICE. Audit: `EXAM_SUBMITTED` |
| `GET` | `/sessions/{id}` | Auth | Detall (STUDENT: solo la seva) |
| `GET` | `/sessions/exam/{examId}` | PROFESSOR/ADMIN | Totes les sessions d'un examen |
| `GET` | `/sessions/exam/{examId}/monitor` | PROFESSOR/ADMIN | Vista monitor (`MonitorDto`) |

### 4.4 Execucions de codi

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `POST` | `/executions/{answerId}/run` | Auth | Executa el codi en Docker. Rate limit: 10/min/usuari. STUDENT: solo les seves |
| `PATCH` | `/executions/{answerId}/score` | PROFESSOR/ADMIN | Estableix puntuació manual (0..max). PROFESSOR: solo exàmens propis |

### 4.5 Grups

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/grups` | PROFESSOR/ADMIN | Llista (ADMIN: tots; PROFESSOR: els seus) |
| `POST` | `/grups` | PROFESSOR/ADMIN | Crea grup |
| `PUT` | `/grups/{id}/students` | PROFESSOR/ADMIN | Estableix alumnes (reemplaça llista completa) |
| `POST` | `/grups/{grupId}/assignar-examen/{examId}` | PROFESSOR/ADMIN | Crea sessions per als alumnes del grup |
| `PATCH` | `/grups/{id}/modul/{modulId}` | PROFESSOR/ADMIN | Assigna mòdul al grup |
| `DELETE` | `/grups/{id}` | PROFESSOR/ADMIN | Elimina grup |

### 4.6 Estructura acadèmica

**Departaments** (`/api/departaments`):
- `GET /` — PROFESSOR/ADMIN
- `POST /` — ADMIN — `{ nom }`
- `DELETE /{id}` — ADMIN
- `GET /{id}/professors` — PROFESSOR/ADMIN
- `POST /{id}/professors` — ADMIN — `{ professorId, esCap }`
- `DELETE /{id}/professors/{professorId}` — ADMIN

**Cicles** (`/api/cicles`):
- `GET /` — Auth — filtrable per `?departamentId=`
- `POST /` — ADMIN — `{ codi, nom, departamentId }`
- `PUT /{id}` — ADMIN — `{ codi, nom, departamentId }`
- `DELETE /{id}` — ADMIN

**Mòduls** (`/api/moduls`):
- `GET /` — Auth — filtrable per `?cicleId=` o `?departamentId=`
- `POST /` — ADMIN — `{ codi, nom, cicleId }`
- `PUT /{id}` — ADMIN — `{ codi, nom, cicleId }`
- `DELETE /{id}` — ADMIN
- `GET /{modulId}/imparticions` — PROFESSOR/ADMIN
- `POST /{modulId}/imparticions` — ADMIN — `{ professorId, curs }`
- `DELETE /{modulId}/imparticions/{id}` — ADMIN

**Matrícules** (`/api/matricules`):
- `GET /` — PROFESSOR/ADMIN — filtrable per `?alumneId=` o `?modulId=&curs=`
- `POST /` — ADMIN — `{ alumneId, modulId, curs }`
- `DELETE /{id}` — ADMIN

### 4.7 Usuaris

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/users` | PROFESSOR/ADMIN | Tots els usuaris |
| `GET` | `/users/role/{role}` | PROFESSOR/ADMIN | Filtrats per rol |
| `POST` | `/users` | PROFESSOR/ADMIN | Crea usuari. Audit: `USER_CREATED` |
| `DELETE` | `/users/{id}` | ADMIN | Elimina. Audit: `USER_DELETED` |
| `PATCH` | `/users/{id}/password` | ADMIN | Canvia contrasenya (BCrypt, mínim 6 caràcters). Audit: `PASSWORD_RESET` |
| `POST` | `/users/import` | ADMIN/PROFESSOR | Importa CSV (`name,email,password,role`). `?role=` override opcional. Retorna `{ created, skipped, errors }` |

### 4.8 Invitacions

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `POST` | `/invitacions` | PROFESSOR/ADMIN | Crea link `{ modulId, curs, maxUses?, grupId? }` |
| `GET` | `/invitacions` | PROFESSOR/ADMIN | Llista (filtrable per `?modulId=&curs=` o les pròpies) |
| `DELETE` | `/invitacions/{id}` | PROFESSOR/ADMIN | Desactiva |
| `GET` | `/invitacions/publica/{token}` | Pública | Info pública del link |
| `POST` | `/invitacions/publica/{token}/acceptar` | Pública | Registra alumne nou + matrícula + JWT |
| `POST` | `/invitacions/publica/{token}/unir-se` | STUDENT | Alumne autenticat s'afegeix |

### 4.9 Aules

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/aules` | PROFESSOR/ADMIN | Llista |
| `POST` | `/aules` | ADMIN | `{ nom, xarxaCidr }` |
| `PUT` | `/aules/{id}` | ADMIN | `{ nom, xarxaCidr }` |
| `DELETE` | `/aules/{id}` | ADMIN | |

### 4.10 Fitxers de pregunta

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/questions/{questionId}/files` | Auth | Llista fitxers |
| `POST` | `/questions/{questionId}/files` | PROFESSOR/ADMIN | Puja fitxer (multipart `file`) |
| `DELETE` | `/questions/{questionId}/files/{fileId}` | PROFESSOR/ADMIN | Elimina fitxer |
| `GET` | `/files/{fileId}/download` | Auth | Descàrrega com a `attachment` |

### 4.11 Configuració del sistema

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/configuracio` | Pública | Retorna `ConfiguracioDto` (nom, logo, colors) |
| `GET` | `/configuracio/logo` | Pública | Retorna binary del logo amb MIME correcte; 404 si no existeix |
| `PUT` | `/configuracio` | ADMIN | Actualitza nom, colors, curs, durada, penalització, focus threshold, gràcia, dominis OAuth |
| `POST` | `/configuracio/logo` | ADMIN | Puja logo (multipart `file`, `image/*`, màx 500 KB) |
| `DELETE` | `/configuracio/logo` | ADMIN | Elimina logo |

### 4.12 Exportació

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/export/exam/{examId}/csv` | PROFESSOR/ADMIN | CSV de correccions; valida propietat; CSV injection sanititzat |

Columnes CSV: `alumne`, `email`, `pregunta`, `tipus`, `punts_max`, `resposta`, `output_exec`, `auto_score`, `manual_score`.

### 4.13 Auditoria

| Mètode | Ruta | Auth | Descripció |
|--------|------|------|------------|
| `GET` | `/audit` | ADMIN | Llista paginada (`page`, `size` màx 200). Filtrable per `?action=`. Ordre: `createdAt DESC` |

---

## 5. Motor d'execució de codi (Docker Sandbox)

### 5.1 Arquitectura

Port `ScriptExecutor` → implementació `DockerScriptExecutor`.

Per a cada execució:
1. Es crea un directori temporal únic: `{execution.scripts-host-path}/{uuid}/`.
2. S'escriu el codi al fitxer corresponent (`script.sh`, `script.ps1`, `Main.java`).
3. S'executa `docker run --rm` amb aïllament complet.
4. L'output es llegeix amb timeout. Es trunca a 10.000 caràcters.
5. El directori temporal s'elimina sempre (bloc `finally`).

### 5.2 Flags de Docker (seguretat)
```
--rm
--network=none
--memory={memoryLimit}
--cpus={cpus}
--security-opt no-new-privileges:true
-v {execDir}:/workspace:ro
--tmpfs=/tmp:rw,size=128m
[--read-only   (excepte Java)]
[-v {filesDir}:/data/files:ro   (si hi ha fitxers de dades)]
```

### 5.3 Imatges i execució per tipus

| Tipus | Variable d'entorn de la imatge | Execució dins el contenidor |
|-------|-------------------------------|----------------------------|
| BASH_CMD, BASH_SCRIPT | `execution.bash-image` | `sh script.sh` |
| PS_CMD, PS_SCRIPT | `execution.powershell-image` | `pwsh -File script.ps1` |
| JAVA_PROG | `execution.java-image` | `javac /tmp/Main.java && java -cp /tmp Main` |

Per a scripts amb `testScript`: el test s'executa amb la variable d'entorn `SCRIPT_FILE` apuntant al codi de l'alumne.

### 5.4 Límits configurables

| Variable | Significat |
|----------|-----------|
| `execution.timeout-seconds` | Timeout del contenidor |
| `execution.memory-limit` | Memòria màxima (ex. `128m`) |
| `execution.cpus` | CPUs assignades (ex. `0.5`) |
| `execution.scripts-host-path` | Directori de treball al host |
| `execution.files-host-path` | Directori de fitxers de dades al host |

Validació: `memoryLimit` i `cpus` es validen contra regex segures abans d'usar-los com a arguments de Docker.

### 5.5 Rate limiting

`ExecutionRateLimiter` (Bucket4j, in-memory per usuari): **10 execucions/minut/usuari**. Token bucket amb reomplerta greedy. `ConcurrentHashMap<UUID, Bucket>`.

---

## 6. Parser de Markdown (`MarkdownExamParser`)

### 6.1 Format d'entrada

```markdown
# Títol de l'examen
durada: 60
instruccions: Llegeix atentament cada pregunta.
---
### 1. Primera secció

## 1 [SHORT] [pts:1]
Enunciat de la pregunta.
:::model
resposta esperada
:::

## 2 [CHOICE] [pts:2]
Quina és la resposta correcta?
- a) Opció incorrecta
- b) Opció correcta
- c) Altra incorrecta
:::model
b
:::

## 3 [BASH_SCRIPT] [pts:3]
Escriu un script que mostri "Hola".
:::output-contains
Hola
:::

## 4 [JAVA_PROG] [pts:4]
Programa que imprimeixi "OK".
:::test
#!/bin/bash
output=$(java -cp /tmp Main)
echo "$output" | grep -q "OK"
:::
```

### 6.2 Regles del parser

- La capçalera és tot el que hi ha **abans** del primer `---`.
- `durada` i `instruccions` a la capçalera són opcionals.
- `### Títol` genera una pregunta de tipus `SECTION` (no puntua, no té `punts`).
- `## N [TIPUS] [pts:X]` defineix una pregunta. El `N` és l'ordre.
- Blocs `:::nom ... :::` extreuen camps específics:
  - `:::model` → `modelResposta` / `correctChoice`
  - `:::output-contains` → `outputContains`
  - `:::output-exact` → `outputExact`
  - `:::output-regex` → `outputRegex`
  - `:::test` → `testScript`
- Per a CHOICE: opcions detectades per regex `^-\s+[a-dA-D]\).*`. El bloc `:::model` és **obligatori**.
- **Validació**: la suma de `punts` de totes les preguntes no-SECTION ha de ser exactament **10.00**.

---

## 7. Seguretat

### 7.1 Autenticació

- **JWT stateless**: `JwtFilter` intercepta cada petició, valida el token i carrega el `UserDetails`.
- **OAuth2 Google**: condicional (`@ConditionalOnExpression`) — s'activa sols si `GOOGLE_CLIENT_ID` i `GOOGLE_CLIENT_SECRET` estan definits.
  - `OAuth2SuccessHandler`: valida domini de l'email contra `dominisOauth` (config del sistema). Crea usuari STUDENT si no existeix. Vincula `oauthProvider/oauthSubject` si l'usuari ja existia.
- **BCrypt** per a contrasenyes locals.

### 7.2 Endpoints públics (sense auth)
- `POST /api/auth/login`
- `GET /api/invitacions/publica/**`
- `POST /api/invitacions/publica/**/acceptar`
- `GET /api/configuracio`
- `GET /api/configuracio/logo`
- OAuth2: `/oauth2/**`, `/login/oauth2/**`
- `OPTIONS /**` (CORS preflight)

### 7.3 Proteccions HTTP

| Mecanisme | Valor |
|-----------|-------|
| CSRF | Desactivat (API stateless) |
| CORS | Orígens explícits (`app.allowed-origins`), `credentials=true`, `maxAge=3600` |
| CSP | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'` |
| Referrer-Policy | `strict-origin-when-cross-origin` |
| X-Frame-Options | `DENY` |

### 7.4 Autorització per roles

| Acció | ADMIN | PROFESSOR | STUDENT |
|-------|-------|-----------|---------|
| Gestió d'usuaris | ✓ crear/editar/eliminar | ✓ crear/llistar | — |
| Importar CSV | ✓ | ✓ | — |
| Estructura acadèmica (CRUD) | ✓ | — lectura | — |
| Crear/editar exàmens | ✓ | ✓ (propis) | — |
| Veure sessions de tots | ✓ | — (sols propis) | — |
| Monitor | ✓ (tots) | ✓ (propis) | — |
| Exportar CSV | ✓ | ✓ (propis) | — |
| Fer exàmens | — | — | ✓ |
| Configuració del sistema | ✓ | — | — |
| Auditoria | ✓ | — | — |

### 7.5 Restricció per IP/CIDR

`IpUtil`: extreu IP real del client des de `X-Forwarded-For` (primer valor no privat) o `remoteAddr`.  
`CidrUtil`: comprova si una IP pertany a un bloc CIDR (IPv4).  
Ambdós tenen tests unitaris (`IpUtilTest`, `CidrUtilTest`).

---

## 8. Frontend

### 8.1 Tecnologia i estructura

- **React 18** + **TypeScript** + **Vite**
- **TailwindCSS** amb tema dinàmic per `colorMarca`
- **Monaco Editor** per a codi
- **Axios** per a HTTP

### 8.2 Rutes de l'aplicació

| Ruta | Pàgina | Rols |
|------|--------|------|
| `/login` | `LoginPage` | Pública |
| `/oauth-callback` | `OAuthCallbackPage` | Pública |
| `/invitacio/:token` | `AcceptarConvitPage` | Pública |
| `/professor/exams` | `ExamsPage` | PROFESSOR, ADMIN |
| `/professor/exams/new` | `ExamCreatePage` | PROFESSOR, ADMIN |
| `/professor/exams/sintaxi` | `ExamSintaxiPage` | PROFESSOR, ADMIN |
| `/professor/exams/:id/preview` | `ExamPreviewPage` | PROFESSOR, ADMIN |
| `/professor/exams/:id/corrections` | `CorrectionPage` | PROFESSOR, ADMIN |
| `/professor/exams/:id/monitor` | `MonitorPage` | PROFESSOR, ADMIN |
| `/professor/grups` | `GrupsPage` | PROFESSOR, ADMIN |
| `/professor/convits` | `ConvitsPage` | PROFESSOR, ADMIN |
| `/admin/users` | `UsersPage` | ADMIN, PROFESSOR |
| `/admin/estructura` | `EstruturaPage` | ADMIN |
| `/admin/matricules` | `MatriculesPage` | ADMIN |
| `/admin/aules` | `AulesPage` | ADMIN |
| `/admin/configuracio` | `ConfiguracioPage` | ADMIN |
| `/admin/audit` | `AuditPage` | ADMIN |
| `/student/exams` | `ExamListPage` | STUDENT |
| `/student/exams/:id` | `ExamTakePage` | STUDENT |
| `/student/sessions/:id/results` | `ExamResultsPage` | STUDENT |

Redirect des de `/` per rol: ADMIN→`/admin/users`, PROFESSOR→`/professor/exams`, STUDENT→`/student/exams`.

### 8.3 Contexts globals

**`AuthContext`**: estat d'autenticació. Persistit a `localStorage` (`auth`, `token`). Format: `{ userId, name, email, role, token }`. L'interceptor Axios injecta `Authorization: Bearer {token}`. En 401 → redirect `/login`.

**`ConfiguracioContext`**: carrega `GET /api/configuracio` a l'inici. Aplica `colorMarca` (hex) convertit a HSL com a variables CSS (`--brand-h`, `--brand-s`) per al tema dinàmic. Fallback si error de xarxa.

### 8.4 Pàgines del professor

**`ExamsPage`** — Llista d'exàmens amb estats visuals:
- Esborrany (gris): Programar, Activar, Assignar mòdul/aula, Previsualitzar, Eliminar
- Programat (blau): editar programació, cancel·lar programació
- En curs/Publicat (verd parpellejant): Desactivar, Assignar a grup, Monitor, Correccions, Tancar
- Finalitzat (gris fosc): Exportar CSV

Modals: Programar (data, hora, grup, validació de conflicte al backend), Assignar mòdul, Assignar aula, Assignar a grup.

**`ExamCreatePage`** — Upload de fitxer `.md`. Mostra errors de parse (ex. "La suma de punts és 8, ha de ser 10").

**`ExamSintaxiPage`** — Referència interactiva del format Markdown. Conté: capçalera, preguntes, tipus disponibles, blocs especials, exemple CHOICE, regles.

**`ExamPreviewPage`** — Vista prèvia completa de l'examen:
- Toggle "Vista alumne" / "Vista professor" (respostes en verd).
- Penalització CHOICE: cap / 25% / 33% / 50% / 100%.
- Gestió de fitxers de dades per pregunta.
- Edició de `correctChoice` i `anulada` inline.

**`GrupsPage`** — Dues pestanyes:
- Grups: crear, editar alumnes (modal multi-checkbox), assignar mòdul, eliminar.
- Alumnes: llista amb grups assignats, botó "Assignar" (modal multi-checkbox de grups).

**`MonitorPage`** — Actualització cada 15 s + botó manual. Resum: total/en curs/entregats. Taula "En curs": alumne, IP, respostes completades, pèrdues de focus (groc/vermell), temps actiu (live cada 1 s). Taula "Entregats": nom, IP, focus, hora. Botó "Reobrir accés" (amb confirmació).

**`CorrectionPage`** — Dues pestanyes:
- Preguntes: estadístiques per pregunta; accions Canviar correcta (re-corregeix tot), Anular/Restaurar.
- Alumnes: sidebar amb llista + puntuació acumulada. Detall per alumne: cada pregunta amb puntuació auto (CHOICE: ✓/✗), input manual (blur → PATCH), botó executar, preview HTML/CSS en iframe, output d'execució en terminal dark.

**`ConvitsPage`** — Formulari per generar links (mòdul, curs, grup opcional, max usos). Llista d'actius (copiar, desactivar) i historial.

### 8.5 Pàgines de l'alumne

**`ExamListPage`** — Si exactament 1 examen: redirect automàtic. Si 0: pantalla d'espera amb spinner + polling cada 15 s. Si ≥2: llista.

**`ExamTakePage`** — Pàgina central de l'examen:
- Header: títol, durada, nombre de preguntes, nom+email, comptador regressiu (vermell <5 min).
- Barrejament seeded per `sessionId`: `shuffleWithinSections` (les seccions es mantenen en ordre, les preguntes dins de cada secció es barregen amb hash del sessionId per a consistència).
- Autosave: 500 ms de debounce per pregunta.
- Editors: Monaco Editor (vs-dark) per a BASH/PS/JAVA; 60 px per a comandes, 300 px per a scripts.
- Java stub pre-emplenat: `public class Main { public static void main(String[] args) { ... } }`.
- HTML/CSS: editor + preview en iframe `sandbox="allow-scripts"`.
- Execució: botó, spinner, resultat (exit code, durada, output terminal dark).
- Fitxers: llista de links de descàrrega + nota `/data/files/`.
- Auto-submit quan `secondsLeft <= 0`.
- `beforeunload`: avís si no s'ha entregat.
- Errors amigables: fora de finestra, aula incorrecta, no matriculat.

**`ExamResultsPage`** — Resum: puntuació automàtica i manual per pregunta. Preguntes anulades marcades. Pendent si sense correcció.

### 8.6 Pàgines d'administrador

**`UsersPage`** — Importar CSV, crear usuari, taula de tots, canviar contrasenya (mínim 6), eliminar.

**`EstruturaPage`** — Tres pestanyes (Departaments / Cicles / Mòduls). Departaments: CRUD + professors (modal afegir/treure, marcar cap). Cicles i Mòduls: CRUD inline. Mòduls: modal imparticions (professor + curs).

**`MatriculesPage`** — Selector mòdul+curs → llista matriculats + afegir/treure.

**`AulesPage`** — CRUD. Validació CIDR al frontend: `/^(\d{1,3}\.){3}\d{1,3}\/(\d|[1-2]\d|3[0-2])$/`.

**`ConfiguracioPage`** — Nom centre, color de marca (color picker + hex + preview), logo (upload/delete, màx 500 KB), curs actiu, durada defecte, penalització, llindar focus, grace period, dominis OAuth.

**`AuditPage`** — Taula paginada (50/pàgina), filtre per acció, columnes: data, acció (badge), recurs, IP, userId.

### 8.7 Pàgina pública d'invitació

**`AcceptarConvitPage`** — Mostra info del mòdul (codi, nom, cicle, departament, curs). Si STUDENT autenticat: "Unir-me". Si no autenticat: formulari registre (nom, email, contrasenya ≥8). En acceptar: auto-login + redirect a `/student/exams`.

### 8.8 Layout

Sidebar fixe (esquerra, 14 rem, fons `brand-700`): logo + nom centre + curs actiu, navegació per rol, peu amb nom/email/rol/logout.

Exam mode (`examMode=true`): header simple sense sidebar, contingut centrat (max-w-3xl).

### 8.9 Tema de color dinàmic

`colorMarca` (hex) → conversió a HSL → variables CSS `--brand-h` i `--brand-s` → classes TailwindCSS `brand-{shade}` usen `hsl(var(--brand-h) var(--brand-s) {lightness})`.

---

## 9. Tests

21 fitxers de test. Estructura:

### Tests de servei (JUnit 5 + Mockito)
- `ExamServiceTest` — creació, publicació, tancament, programació, conflictes
- `ExamServiceAssignModulTest` — assignació de mòdul amb validació d'impartició
- `SessionServiceTest` — inici de sessió, finestra d'accés, aula CIDR, matrícula
- `CorrectionAutoScoreTest` — autocorrecció CHOICE (correcta/incorrecta/buida/penalització), output criteris
- `GrupServiceTest` — creació, setStudents, assignar examen, filtrat per rol
- `UserServiceTest` — creació, importació CSV
- `MatriculaServiceTest` — matrícula, desmatrícula, validació de rol
- `AulaServiceTest`, `CicleServiceTest`, `ModulServiceTest` — CRUD bàsic
- `DepartamentServiceTest` — professors, cap de departament
- `InvitacioServiceTest` — creació, acceptar, unir-se, caducitat
- `ExamSchedulerServiceTest` — activació, tancament automàtic, sessions expirades

### Tests d'infraestructura
- `MarkdownExamParserTest` — format vàlid, errors de punts, CHOICE sense model, tipus
- `DockerScriptExecutorTest` — execució bash, Java, test script, timeout

### Tests d'integració (Testcontainers + PostgreSQL real)
- `ExamRepositoryIntegrationTest`
- `SessionRepositoryIntegrationTest`
- `SchemaIntegrationTest` — verifica que les 22 migracions Flyway s'apliquen correctament

### Tests de seguretat
- `OAuth2DomainRestrictionTest` — dominis permesos, domini rebutjat
- `IpUtilTest` — X-Forwarded-For, IPs privades, IPv6
- `CidrUtilTest` — rangs CIDR, IPs límit

---

## 10. Infraestructura

`docker-compose.yml` a `/infra/`. Tres serveis:

| Servei | Notes |
|--------|-------|
| `backend` | Spring Boot, port 8080 |
| `postgres` | PostgreSQL, volum persistent |
| `frontend` | Nginx servint el build de Vite |

### Variables d'entorn rellevants

```env
# JWT
JWT_SECRET=
JWT_EXPIRATION_MS=86400000

# OAuth2 (opcional)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# CORS
APP_ALLOWED_ORIGINS=http://localhost:5173

# Docker sandbox
EXECUTION_SCRIPTS_HOST_PATH=/tmp/exams/scripts
EXECUTION_FILES_HOST_PATH=/tmp/exams/files
EXECUTION_TIMEOUT_SECONDS=10
EXECUTION_MEMORY_LIMIT=128m
EXECUTION_CPUS=0.5
EXECUTION_BASH_IMAGE=bash:5
EXECUTION_POWERSHELL_IMAGE=mcr.microsoft.com/powershell:latest
EXECUTION_JAVA_IMAGE=eclipse-temurin:21-jdk

# Frontend
VITE_API_URL=http://localhost:8080
```

---

*Document generat a partir del codi font complet. Setembre 2026.*

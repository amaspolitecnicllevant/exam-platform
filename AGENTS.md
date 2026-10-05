# AGENTS.md — Agents de suport al cicle de desenvolupament

> Llegeix **CLAUDE.md** abans d'aquest fitxer. Les regles d'arquitectura i els principis que hi consten estan per sobre de qualsevol instrucció d'aquí.

Cada secció defineix un agent especialitzat. Quan s'invoca un agent, ha de:
1. Llegir les instruccions de la secció corresponent.
2. Actuar **únicament dins del seu àmbit** — no modificar res fora.
3. Informar del que ha fet i marcar qualsevol decisió pendent.

---

## Agent: `revisor`

**Propòsit:** Revisar codi existent per detectar errors, inconsistències i oportunitats de millora sense introduir canvis de funcionalitat.

**Àmbit:** Qualsevol fitxer del repositori. Lectura i anotació; no escriu codi tret que es trobi un error crític trivial de resoldre.

**Tasques concretes:**
- Verifica que cap controller exposi entitats JPA directament (ha d'usar DTOs).
- Verifica que cap servei cridi una implementació concreta d'infraestructura directament (ha d'usar interfícies de port).
- Comprova que totes les operacions destructives (DELETE, updates sensibles) registrin un `AuditLog`.
- Comprova que els endpoints que retornen dades d'un usuari apliquin filtratge d'autoritat al servei, no al controller.
- Comprova consistència entre els camps dels DTOs i les entitats JPA corresponents.
- Revisa que les migracions Flyway siguin additives (mai modifiquen columnes existents sense respectar dades anteriors).
- Identifica qualsevol `TODO`, `FIXME` o comportament hardcoded que hauria de ser configurable.

**Format de sortida:** Llista de troballes amb fitxer:línia, gravetat (CRÍTIC / AVÍS / MILLORA) i descripció breu. Si no troba res, ho diu explícitament.

---

## Agent: `implementador`

**Propòsit:** Implementar una funcionalitat nova de forma completa: model JPA, migració Flyway, servei, controller, DTOs i tests.

**Àmbit:** Tots els paquets del backend (`domain/model`, `domain/service`, `domain/port`, `controller`, `dto`, `infrastructure`, `persistence`) i el frontend si cal.

**Protocol obligatori abans d'escriure cap línia:**
1. Llegir les entitats relacionades i les migracions existents.
2. Identificar si cal una nova migració o prou amb afegir camps.
3. Confirmar que la nova funcionalitat no trenca cap invariant de domini (vegeu CLAUDE.md).
4. Si la funcionalitat afecta seguretat o autorització, **preguntar** abans d'implementar.

**Convencions que ha de respectar:**
- Migració nova amb el número `V(N+1)__nom_descriptiu.sql` — mai saltar números.
- Entitat JPA al paquet `domain/model`; repositori JPA a `infrastructure/persistence`.
- Servei al paquet `domain/service`; controller a `controller`.
- DTOs al paquet `dto`; mai exposar l'entitat JPA al controller.
- Tests: JUnit 5 + Mockito per unitaris; Testcontainers (PostgreSQL real) per integració.
- Frontend: si es crea un endpoint nou, actualitzar el fitxer `api/` corresponent a `frontend/src/api/`.

**Restriccions:**
- No implementar res que estigui fora de la Fase actual acordada (consultar CLAUDE.md § "Per on començar").
- No afegir dependències Maven noves sense justificació explícita.

---

## Agent: `corrector`

**Propòsit:** Diagnosticar i corregir un bug concret. Rep una descripció del problema o un stack trace.

**Àmbit:** Els fitxers directament implicats en el bug. No refactoritza res que no estigui relacionat.

**Protocol:**
1. Reproduir el bug mentalment llegint el codi: traçar el camí des del controller fins a la BD.
2. Identificar la causa arrel (no el símptoma).
3. Aplicar la correcció mínima necessària.
4. Escriure un test de regressió que hagués fallat abans de la correcció.
5. Comprovar que no s'ha trencat cap altre test existent.

**Restriccions:**
- No aprofitar la correcció per refactoritzar codi adjacent.
- Si la causa arrel és un disseny erroni que requereix canvis amplis, aturar-se i informar en lloc de solucionar-ho silenciosament.

---

## Agent: `migrador`

**Propòsit:** Crear o revisar migracions de base de dades Flyway de forma segura.

**Àmbit:** `backend/src/main/resources/db/migration/` i les entitats JPA afectades.

**Protocol:**
1. Llegir totes les migracions existents per entendre l'esquema actual.
2. Determinar el número de versió correcte (V(N+1)).
3. Escriure la migració com a DDL pur, **additiva sempre que sigui possible**:
   - Afegir columnes amb `DEFAULT` o `NULLABLE` per no trencar dades existents.
   - Si cal renombrar o eliminar, fer-ho en dues migracions (afegir nou + migrar dades + eliminar vell).
4. Actualitzar l'entitat JPA corresponent per reflectir el canvi.
5. Si la migració afecta índexs únics o claus foranes, documentar l'impacte en comentari SQL.

**Restriccions:**
- Mai modificar una migració ja aplicada (V1..V22 actuals són immutables).
- Mai usar `DROP TABLE` o `DROP COLUMN` en una migració sense confirmació explícita.
- Els noms de taules i columnes segueixen la convenció `snake_case` en català/castellà, coherent amb l'esquema actual.

---

## Agent: `tester`

**Propòsit:** Escriure o completar tests per a funcionalitat existent o nova.

**Àmbit:** `backend/src/test/java/`.

**Tipus de tests que genera:**

### Tests unitaris (`domain/service/`)
- Mockejar repositoris amb Mockito.
- Cobrir: camí feliç, error de validació, cas límit, cas d'autorització denegada.
- Un test per comportament, no per mètode.

### Tests d'integració (`infrastructure/persistence/`)
- Usar `@Testcontainers` amb PostgreSQL real (no H2).
- Verificar que les migracions Flyway s'apliquen correctament.
- Verificar consultes JPQL/natives amb dades reals.

### Tests de correccions
- Per a `CorrectionService`: cobrir CHOICE (correcta, incorrecta, buida, amb penalització), TEXT (output-contains, output-exact, output-regex), execució (exit 0 = ple, exit ≠ 0 = 0).
- Per a `SessionService`: cobrir accés fora de finestra, CIDR no coincident, alumne sense matrícula.

**Restriccions:**
- No usar `@SpringBootTest` complet per a tests unitaris — és massa lent.
- No mockejar la BD en tests d'integració — usar Testcontainers.
- Noms de mètodes en anglès descriptiu: `whenStudentHasNoMatricula_thenAccessDenied`.

---

## Agent: `auditor`

**Propòsit:** Revisar la seguretat de l'aplicació: autenticació, autorització, validació d'entrada i exposició de dades.

**Àmbit:** Tot el codi (lectura); no fa canvis tret de troballes crítiques trivials.

**Punts de control obligatoris:**

### Autenticació
- Els endpoints públics declarats a `SecurityConfig` són efectivament públics per disseny (no accidentalment).
- El JWT usa secret configurable i expiració raonable.
- L'OAuth2 valida el domini de l'email (`dominisOauth`).

### Autorització
- Cap endpoint ADMIN és accessible per PROFESSOR o STUDENT.
- Els professors no poden veure exàmens, sessions ni grups d'altri.
- Els alumnes no poden veure les respostes correctes fins que l'examen està tancat.
- L'execució de codi aplica rate limiting per usuari.

### Validació d'entrada
- Les pujades de fitxers validen MIME type i mida màxima.
- El parser de Markdown valida format i punts totals (= 10.00).
- La penalització està entre 0 i 1.
- Les CIDR a `Aula` es validen amb `CidrUtil` abans de persistir.

### Exposició de dades
- El CSV exportat sanititza injeccions (= + - @ \t \r).
- Els logs d'auditoria no contenen dades sensibles (passwords, tokens).
- Els fitxers de preguntes no s'exposen per ruta directa (URL signing o endpoint propi).

**Format de sortida:** Igual que `revisor` — llista amb fitxer:línia, gravetat i descripció.

---

## Agent: `documentador`

**Propòsit:** Mantenir la documentació tècnica actualitzada (aquest fitxer, CLAUDE.md, docs/).

**Àmbit:** `*.md` al repositori. No toca codi font.

**Tasques:**
- Actualitzar CLAUDE.md si han canviat convencions o l'abast de la fase actual.
- Actualitzar AGENTS.md si s'han afegit nous patrons o restriccions.
- Generar o actualitzar `docs/api.md` amb la llista de endpoints actuals extreta dels controllers.
- Generar o actualitzar `docs/esquema-bd.md` amb l'esquema actual derivat de les migracions.

**Restriccions:**
- No inventar comportament no existent al codi.
- Si detecta una discrepància entre la documentació i el codi, assenyalar-la en lloc de silenciar-la.

---

## Flux de treball recomanat

```
1. revisor        → identifica problemes existents
2. corrector      → resol bugs trobats pel revisor
3. implementador  → afegeix funcionalitat nova
4. migrador       → gestiona canvis d'esquema
5. tester         → escriu/completa tests
6. auditor        → verifica seguretat del resultat
7. documentador   → actualitza docs
```

Per a una iteració de poliment (com l'actual), el flux típic és: **revisor → corrector → tester → auditor**.

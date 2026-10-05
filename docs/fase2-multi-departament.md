# Fase 2 — Extensió multi-departament / centre complet

## Noves entitats

| Entitat | Relacions clau |
|---|---|
| `Departament` | professors (n:m amb User) |
| `Cicle` | pertany a Departament (DAW, SMX, ASIX...) |
| `Modul` | pertany a Cicle (0483 SI, 0484 BD...) |
| `Imparticio` | Professor ↔ Modul ↔ curs |
| `Matricula` | Alumne ↔ Cicle ↔ curs |

## Migracions BD necessàries

- V7: departaments + departament_professors (n:m)
- V8: cicles
- V9: moduls
- V10: imparticions
- V11: matricules
- V12: ALTER grups ADD modul_id, ALTER exams ADD modul_id

## Fitxers existents a modificar (~12)

- `ExamService` — assertOwnership comprova impartició del mòdul
- `GrupService` — ídem
- `SessionService` — alumnes filtrats per matrícula
- `ExamRepository` — findPublished() filtra per mòduls de l'alumne
- `Exam.java` — afegir `modul: Modul`
- `Grup.java` — afegir `modul: Modul`
- `ExamDto` — afegir modulNom, cicleNom
- `ExamsPage.tsx` — selector de mòdul en crear/programar
- `GrupsPage.tsx` — grups lligats a mòdul
- `UsersPage.tsx` — gestió departaments/imparticions
- `types/index.ts` — nous tipus

## Fitxers nous (~30)

**Backend (18):** entitats x5, repositoris x5, serveis x3, controladors x3, DTOs x2

**Frontend (12):** DepartamentsPage, CiclesPage, ModulsPage, MatriculesPage, API x4, tipus x3+

## Decisions pendents (cal resoldre abans de començar)

1. **Professor a més d'un departament?** — Probable sí (n:m)
2. **Alumne a més d'un cicle?** — Infreqüent; decidir si nullable o taula separada
3. **Cap de departament** — rol nou o flag en professor?
4. **Migració de grups existents** — com s'assignen els grups actuals a mòduls?

## Risc principal

Capa d'autorització: tots els `assertOwnership` passen de comparar `createdBy == user`
a consultar `Imparticio`. Cal revisió sistemàtica + tests d'integració exhaustius.

## Estimació

- ~35% de l'app cal tocar
- ~1.850 línies noves/modificades sobre ~3.200 actuals
- Nucli intacte: parser, sessions, correccions, planificació, JWT
- Estimació: 3-4 setmanes de desenvolupament incremental

## Estat

**COMPLETADA** — 2026-09-14.

Tot el pla implementat. Decisions resoltes:
1. Professor a més d'un departament → sí, n:m via `ProfessorDepartament` amb flag `esCap`.
2. Alumne a més d'un mòdul → sí, `Matricula` per mòdul i curs (V16).
3. Cap de departament → flag `esCap` a la taula de relació.
4. Grups existents → `modul_id` nullable (V14); assignació manual via UI.

Afegit respecte al pla original:
- V15: `professor_departaments` (n:m)
- V17: `invitacions` (links de convit per a auto-matrícula d'alumnes)
- V18: `aules` (restricció d'accés per subxarxa CIDR)
- V19: `client_ip` a sessions (monitor en temps real)
- `assertOwnership` extès per imparticions (professors del mateix mòdul)
- `startOrResume` valida matrícula si l'examen té mòdul
- Tests d'integració Testcontainers (ExamRepository, SessionRepository, Schema)
- 194 tests · 0 errors

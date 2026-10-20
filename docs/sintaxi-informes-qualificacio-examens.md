# Sintaxi dels informes de qualificació dels exàmens (.md)

Document que genera la plataforma d'exàmens (menú **Exportar → Informes de qualificació (.md)**) per explicar la nota de cada alumne en un examen. És Markdown estàndard, pensat per importar-lo a l'aplicació de notes, que ha de mostrar a cada alumne la seva nota **acompanyada de l'informe** que explica d'on surt.

Segueix la mateixa sintaxi que els *informes de qualificació de pràctiques* (`sintaxi-informes-practiques.md`): mateixos títols, mateixes taules, notes amb coma decimal. L'única diferència és que un examen no té rúbrica: la nota surt **només de les preguntes**, repartides en una o més parts.

---

## 1. Elements de Markdown que s'usen (només aquests)

| Element | Ús |
|---|---|
| `#` títol de nivell 1 | Un sol cop: títol de l'informe de la classe |
| `##` títol de nivell 2 | `Resum de la classe` i una secció per alumne (`## Nom Cognoms`) |
| `###` títol de nivell 3 | Seccions de cada alumne (vegeu §2) |
| Taules amb pipes | Resum de la classe, resum de l'alumne, preguntes i competències |
| `**negreta**` | Nota final |
| `*cursiva*` | Fórmula de càlcul |
| Llista amb `-` | «Què pots millorar» |
| `---` | Separació entre alumnes |

No s'usen HTML, imatges, enllaços ni blocs de codi. Les notes són amb **coma decimal** (`6,5`), sense zeros sobrants (`7`, `6,76`) i com a màxim amb dues xifres. Una cel·la no pot portar salts de línia i els `|` del text s'escapen (`\|`).

---

## 2. Estructura del document

```
# Informes de qualificació — <títol de l'examen>

**Mòdul:** <nom del mòdul> (<codi>)

Nota final = <pes>% <part> + <pes>% <part>. Cada part pesa segons els seus punts: <N> preguntes, <X> punts en total.

<frase sobre com es calcula la nota: penalització del test, bonus…>

## Resum de la classe

| Alumne | Teoria | Final |
|---|---:|---:|
| Nom Cognoms | 8 | 8 |
| Altre Alumne | No entregada | — |

---

## Nom Cognoms

### Resum

| Part | Nota (sobre 10) | Pes |
|---|---:|---:|
| Part 1 — Conceptes | 7 | 50% |
| Part 2 — Normativa | 9 | 50% |
| **Nota final** | **8** | |

*Càlcul: 50% × 7 + 50% × 9 = 8*

### Part 1 — Conceptes

2 preguntes, 5 punts (50% de la nota).

| Pregunta | Nota | Per què | Resposta esperada |
|---|---:|---|---|
| 1. Tema | 2 / 2 | Resposta correcta. | — |
| 2. Tema | 1,5 / 3 | Justificació. | Resposta de referència. |

### Per competència

| Competència | Punts | Nota (sobre 10) |
|---|---:|---:|
| CO1 | 3,5 / 5 | 7 |

### Què pots millorar

- **Teoria 2. Tema** (1,5/3): resposta de referència.

---

## Altre Alumne
...
```

Si l'examen **no té seccions**, és una sola part: la taula de resum porta una fila `Part teòrica` amb pes `100%`, la secció es diu `### Part teòrica`, la frase de la fórmula és `Nota final = 100% teoria. Teoria: N preguntes, X punts en total.` i la fórmula és `*Càlcul: 100% × 8 = 8*`. L'exemple complet es pot veure generant l'exportació d'un examen amb seccions.

---

## 3. D'on surt la nota final

- **Nota de l'examen (sobre 10)** = punts obtinguts ÷ punts màxims de l'examen × 10. Cada pregunta compta els seus punts (`[pts:X]`).
- **Parts:** cada **secció** (`### Part …` del fitxer d'importació) és una part. El **pes** d'una part és el percentatge dels punts de l'examen que té; la **nota de la part** és els seus punts sobre els seus màxims, passats a 10. Per això la suma `pes × nota de la part` dona exactament la nota final.
- **Penalització del test:** una resposta errada resta el percentatge configurat dels punts de la pregunta (per exemple −1/3); una en blanc no resta. Per això una part pot tenir una nota negativa.
- **Bonus:** una pregunta anul·lada dona els punts sencers a tothom.
- **Respostes sense qualificar:** compten 0 i l'informe ho diu («Pendent de revisar: de moment compta 0»). La nota és provisional fins que es corregeixin.
- **Competències:** si les preguntes en tenen, hi ha una taula amb els punts i la nota sobre 10 de cada competència. No intervé en la nota final: només l'explica.

---

## 4. Regles

- Un alumne = una secció `##` amb el seu nom complet, en ordre alfabètic. Entre alumnes, una línia `---`.
- **«Pregunta»:** `número. tema`. El número és el que veu l'alumne (no compta les seccions) i el tema és el principi de l'enunciat (60 caràcters com a màxim).
- **«Nota»:** `obtinguts / màxims`. Una pregunta sense resposta surt amb `0 / màx`.
- **«Per què»**, per ordre de preferència:
  1. el comentari que el professor ha escrit a la resposta;
  2. la justificació de la revisió amb IA, si s'ha importat;
  3. els motius de la correcció automàtica (per exemple «−0,5: no esmenta «DNS»»);
  4. un text curt segons el cas: «Resposta correcta.», «Ha triat c; la correcta és b.», «No contestada.», «Pregunta anul·lada: tothom té els punts.».
- **«Resposta esperada»:** només s'omple (`—` si no) quan la nota és inferior al 90 % de la pregunta. Per a un test és l'opció correcta; per a la resta, la resposta model de la pregunta.
- **Què pots millorar:** les preguntes amb menys del 60 % dels punts, amb la resposta esperada. Si no n'hi ha, la línia `- Cap pregunta per sota del 60 % dels punts.`
- Si l'alumne no ha entregat l'examen, la fila del resum diu `No entregada`, la final és `—` i la seva secció només porta el resum.

---

## 5. Com es genera

Des de la plataforma: a **Els meus exàmens**, a l'examen, menú **Exportar → Informes de qualificació (.md)**. Baixa un únic fitxer amb tota la classe. Cada exportació queda al registre d'activitat.

El fitxer porta **noms d'alumnes** i la resposta model de cada pregunta: no el comparteixis fora de les persones que han de veure les notes.

---

## 6. Limitacions

- Un examen no té rúbrica: no hi ha cap secció «Part pràctica» amb criteris de 0 a 10. Per combinar la teoria d'un examen amb una pràctica avaluada amb rúbrica, cal fer servir l'script de les pràctiques (`informe_alumne.py`) amb els CSV de correcció.
- La taula `Resum de la classe` porta les columnes `Teoria` i `Final` (sense `Pràctica`). L'aplicació d'importació ha d'admetre un informe amb una sola columna de nota.
- El tema de cada pregunta és el principi de l'enunciat, no un nom curt triat pel professor.
- Les justificacions es copien tal com són a la plataforma (no es reformulen): revisa-les abans de donar els informes als alumnes.

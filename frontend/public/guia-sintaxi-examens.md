# Guia de sintaxi per crear exàmens en Markdown

Aquesta guia descriu el format exacte que ha de tenir un fitxer `.md` perquè la plataforma l'importi com a examen. Està pensada tant per al professorat com per donar-la a una IA (Claude, ChatGPT…) perquè generi exàmens vàlids. La secció 9 conté les instruccions específiques per a la IA.

---

## 1. Resum ràpid

````markdown
# Títol de l'examen
durada: 60
instruccions: Text opcional per als alumnes.

---

### Part 1 — Teoria

## 1. [choice] [pts:2] [ra:RA1] [dif:baixa]
Enunciat de la pregunta.

- a) Opció A
- b) Opció B
- c) Opció C

:::model
b
:::

## 2. [short] [pts:3]
Enunciat.

:::model
Resposta correcta.
:::

:::clau
concepte 1, sinònim
concepte 2
:::

### Part 2 — Pràctica

## 3. [bash-cmd] [pts:5]
Enunciat.

:::model
comanda de referència
:::

:::output-contains
text esperat a la sortida
:::
````

- La suma de tots els `[pts:X]` ha de ser **exactament 10**.
- Cada bloc `:::nom` s'obre en una línia pròpia i es **tanca amb `:::`** en una línia pròpia, abans d'obrir el següent (vegeu §5).

---

## 2. Capçalera de l'examen

El fitxer comença amb la capçalera, seguida d'una línia `---` que la separa de les preguntes.

| Camp | Obligatori | Descripció |
|:--|:--:|:--|
| `# Títol` | Sí | Títol de l'examen (línia que comença per `# `) |
| `durada: N` | No | Durada en minuts. Si no hi és, 90 |
| `instruccions: text` | No | Text que es mostra a l'alumne a l'inici (una sola línia) |

> La primera línia `---` del fitxer marca el final de la capçalera: no n'hi posis cap abans (ni a les instruccions).

---

## 3. Seccions (opcional)

Una línia que comença per `### ` obre una secció, p. ex. `### Part 1 — Teoria`. Les seccions agrupen preguntes: a cada alumne se li barreja l'ordre de les preguntes **dins de cada secció**, però les seccions no es barregen entre elles.

Si en una secció es poden fer servir apunts (per exemple, la part pràctica), afegeix `[apunts]` al final del títol: `### Part 2 — Pràctica [apunts]`. Totes les preguntes de la secció queden marcades: l'alumne veu que pot fer servir apunts, i l'informe de possibles còpies hi aplica un llindar de semblança més alt.

> **Compte:** qualsevol línia que comenci per `### ` es considera una secció, també dins d'un enunciat. Si necessites un subtítol dins d'un enunciat, usa negreta (`**Context**`) en lloc de `###`.

---

## 4. Capçalera de pregunta

```
## N. [tipus] [pts:X] [ra:RA1] [dif:mitjana] [ordre:fix] [formats:docx,xlsx]
```

| Element | Obligatori | Descripció |
|:--|:--:|:--|
| `N.` | Sí | Número de la pregunta. És orientatiu: la plataforma numera les preguntes segons l'ordre del fitxer |
| `[tipus]` | Sí | Tipus de pregunta (vegeu §6). Majúscules o minúscules, amb guió o guió baix: `bash-cmd` = `BASH_CMD` |
| `[pts:X]` | Sí | Punts de la pregunta. Decimals amb **punt**: `[pts:0.5]`, no `[pts:0,5]` |
| `[ra:NOM]` | No | Resultat d'aprenentatge (p. ex. `[ra:RA1]`). Permet veure la nota per RA |
| `[dif:NIVELL]` | No | Dificultat: només `baixa`, `mitjana` o `alta` |
| `[ordre:fix]` | No | Només per a `choice`: les opcions surten en l'ordre del fitxer (vegeu §6.1) |
| `[apunts]` | No | En aquesta pregunta es poden fer servir apunts (en paper). Vegeu §3 per marcar-ho a tota una secció |
| `[formats:LLISTA]` | No | Només per a `fitxer`: extensions que l'alumne pot pujar, separades per comes (vegeu §6.7) |

- Les etiquetes opcionals van **després** de `[pts:X]`, en qualsevol ordre. `[apunts]` no porta valor.
- No s'admeten altres etiquetes (p. ex. `[pes:1]`) ni altres dificultats: l'examen es rebutja indicant la línia.
- L'enunciat va just després de la capçalera i pot tenir diverses línies i format Markdown (negreta, llistes, taules, `codi`, blocs de codi amb tres accents greus).
- RA, dificultat i barreja d'opcions també es poden editar des de la previsualització de l'examen.

---

## 5. Blocs de correcció

Els blocs van **al final de la pregunta**, després de l'enunciat. Cada bloc:

1. S'obre amb `:::nom` en una línia pròpia.
2. Conté el text del bloc.
3. **Es tanca amb `:::` en una línia pròpia.**

Si una pregunta té més d'un bloc, **tanca cada bloc abans d'obrir el següent**:

```markdown
:::model
ip addr show
:::

:::output-contains
inet
:::
```

> ⚠ **Error freqüent:** encadenar blocs sense tancar-los (`:::model` … `:::output-contains` … `:::`). La plataforma rebutja l'examen indicant la línia del bloc que no s'ha tancat.

- El text d'un bloc no pot contenir `:::`.
- Els blocs van al final: no hi pot haver text de l'enunciat després dels blocs.
- Cada bloc només pot aparèixer una vegada per pregunta.
- Dins dels blocs (p. ex. un script) les línies que comencen per `#`, `##` o `###` són text normal.

| Bloc | Per a | Funció |
|:--|:--|:--|
| `:::model` | tots | `choice`: lletra correcta. Altres: resposta o solució de referència, que el professor veu en corregir |
| `:::clau` | `text`, `short`, `long` | Conceptes clau per proposar la nota (§6.2) |
| `:::output-contains` | preguntes de codi | La sortida ha de contenir cada línia indicada (§6.3) |
| `:::output-exact` | preguntes de codi | La sortida ha de ser exactament aquest text (§6.3) |
| `:::output-regex` | preguntes de codi | La sortida ha de complir aquesta expressió regular (§6.3) |
| `:::test` | `bash-script`, `ps-script` | Script de comprovació (§6.4) |

---

## 6. Tipus de preguntes

| Tipus | Què fa l'alumne | Correcció |
|:--|:--|:--|
| `choice` | Tria una opció (a–d) | Automàtica, amb la penalització configurada a l'examen |
| `short` | Resposta breu | Proposta per conceptes clau (`:::clau`) o manual |
| `text` | Resposta oberta | Proposta per conceptes clau (`:::clau`) o manual |
| `long` | Resposta llarga | Proposta per conceptes clau (`:::clau`) o manual |
| `bash-cmd` | Una comanda bash | Automàtica (`:::output-*`) |
| `ps-cmd` | Una comanda PowerShell | Automàtica (`:::output-*`) |
| `bash-script` | Un script bash | Automàtica (`:::test` o `:::output-*`) |
| `ps-script` | Un script PowerShell | Automàtica (`:::test` o `:::output-*`) |
| `java-prog` | Un programa Java (classe `Main`) | Automàtica (`:::output-*`) |
| `html-css` | Codi HTML/CSS amb previsualització | Manual |
| `fitxer` | Puja un fitxer (Word, Excel, Packet Tracer…) | Manual (vegeu §6.7) |

Les notes automàtiques de les preguntes de text i de codi són **propostes**: el professor les revisa (les accepta o les canvia) abans de publicar les notes.

### 6.1 `choice` — Test

Les opcions s'escriuen a l'enunciat, una per línia, amb el format `- a) text`. Només s'admeten les lletres **a, b, c i d**. La lletra correcta va a `:::model`.

```markdown
## 1. [choice] [pts:1]
Quin port fa servir per defecte un servidor DHCP?

- a) 53
- b) 67
- c) 80
- d) 443

:::model
b
:::
```

- Cada alumne veu les opcions en un **ordre diferent** i amb les lletres reassignades, perquè no es puguin passar "la B". La correcció no canvia.
- Si alguna opció depèn de la posició (p. ex. «Totes les anteriors»), afegeix `[ordre:fix]` a la capçalera perquè no es barregin. Una alternativa és redactar-la sense dependre de l'ordre: «Totes les opcions són correctes».

### 6.2 `short`, `text`, `long` — Resposta escrita

`:::model` conté la resposta correcta, que el professor veu al costat de la de l'alumne.

`:::clau` (opcional) conté els conceptes clau. En entregar, la plataforma proposa una nota i explica què hi falta (p. ex. `−0,5: no esmenta «DNS»`).

- Una línia per concepte. Els **sinònims** se separen per comes: n'hi ha prou que n'aparegui un.
- El **pes** (en punts) és opcional, després de `|`. O tots els conceptes tenen pes i **sumen els punts de la pregunta**, o cap (i els punts es reparteixen a parts iguals).
- La cerca no distingeix majúscules ni accents i compara **paraules senceres**: `IP` no coincideix dins de `tipus`. Un concepte de diverses paraules ha d'aparèixer seguit.

```markdown
## 2. [short] [pts:1]
Què fa un servidor DHCP?

:::model
Assigna automàticament la configuració de xarxa (adreça IP, màscara, porta d'enllaç i DNS) als equips.
:::

:::clau
adreça IP, direcció IP, IP | 0.5
automàtic, automàticament, dinàmic | 0.5
:::
```

### 6.3 Preguntes de codi — criteris de sortida

S'apliquen a `bash-cmd`, `ps-cmd`, `java-prog` i als scripts sense `:::test`. Se'n posa **un de sol** (amb més d'un, l'examen es rebutja). Les preguntes `html-css` no s'executen: no hi posis criteris de sortida.

- **`:::output-contains`**: cada línia del bloc ha d'aparèixer a la sortida (sense distingir majúscules). Amb diverses línies, la proposta és **proporcional** a les que hi apareixen.
- **`:::output-exact`**: la sortida ha de ser exactament aquest text (sense distingir majúscules; s'ignoren espais i salts de línia a l'inici i al final).
- **`:::output-regex`**: la sortida ha de complir l'expressió regular (sintaxi Java, mode multilínia: `^` i `$` marquen inici i final de cada línia). N'hi ha prou que coincideixi una part.

Regles comunes:

- El programa també ha d'**acabar bé** (exit code 0). Amb error o per temps esgotat, la pregunta val 0 encara que la sortida sigui correcta.
- Un `:::output-contains` buit o una `:::output-regex` invàlida fan que l'examen es rebutgi en pujar-lo.
- Sense cap criteri, la pregunta val els punts sencers si el programa acaba sense error.

```markdown
## 3. [bash-cmd] [pts:1]
Escriu una comanda que mostri els números de l'1 al 5, un per línia.

:::model
seq 1 5
:::

:::output-contains
1
3
5
:::
```

### 6.4 `bash-script` i `ps-script` amb `:::test`

`:::test` és un script que comprova la solució de l'alumne. Retorna **exit code 0** si és correcta i qualsevol altre valor si no ho és. La variable `$SCRIPT_FILE` conté la ruta de l'script de l'alumne: el test l'ha d'executar ell mateix (`bash "$SCRIPT_FILE" arguments…`).

```markdown
## 4. [bash-script] [pts:2]
Escriu un script que rebi un nom com a paràmetre i mostri `Hola, <nom>!`.

:::model
#!/bin/bash
echo "Hola, $1!"
:::

:::test
#!/bin/bash
[ "$(bash "$SCRIPT_FILE" Anna 2>/dev/null)" = "Hola, Anna!" ] && exit 0 || exit 1
:::
```

`:::test` només s'admet en `bash-script` i `ps-script`, i no es pot combinar amb un `:::output-*`. En `java-prog` o `bash-cmd`, usa `:::output-*`.

### 6.5 `java-prog`

La classe s'ha de dir `Main` i tenir `public static void main(String[] args)`. Es compila i s'executa, i la sortida es comprova amb `:::output-*`.

### 6.6 Entorn d'execució

El codi de l'alumne (i el `:::test`) s'executa en un contenidor aïllat:

- **Sense xarxa.** No es pot fer `ping`, `curl`, `apt`…
- **Sistema de fitxers de només lectura**, excepte `/tmp`. Els scripts de test que necessitin fitxers els han de crear a `/tmp`.
- Imatges: `bash:5` (Alpine), PowerShell oficial per a Linux i `eclipse-temurin:21` per a Java. No és Windows: no hi ha `C:\`.
- Límits: 128 MB de memòria, mitja CPU i **15 s** per execució (configurable pel centre).
- Els fitxers de dades que el professor adjunti a una pregunta (des de la previsualització) són a `/data/files/`.

### 6.7 `fitxer` — Lliurament d'un fitxer

L'alumne respon pujant un fitxer en comptes d'escriure: un document Word o Excel, una simulació de Packet Tracer, un PDF… La pregunta es **corregeix a mà**: el professor descarrega el fitxer des de la correcció i hi posa la nota. Mentre no la qualifiqui, apareix com a «pendent de revisar» i no es poden publicar les notes.

```
## 5. [fitxer] [pts:3] [formats:docx,pkt]
Dissenya la xarxa de l'enunciat i entrega:
- la simulació de Packet Tracer (`.pkt`),
- un document amb la taula d'adreçament (`.docx`).
```

- `[formats:…]` indica quins fitxers admet la pregunta, per exemple `[formats:docx]` o `[formats:xlsx,pdf]`. Sense l'etiqueta s'admeten tots els formats de la llista.
- **Formats admesos:** `docx`, `xlsx`, `pptx`, `odt`, `ods`, `odp`, `pdf`, `pkt`, `pka`, `pkz` (Packet Tracer), `zip`, `png`, `jpg`, `jpeg` i `txt`. No s'admeten documents amb macros (`docm`, `xlsm`…) ni executables.
- **Un fitxer per pregunta**, de **10 MB com a màxim**. Si l'alumne en puja un altre, substitueix l'anterior; fins que entrega l'examen el pot esborrar o canviar. Si calen diversos fitxers, posa diverses preguntes `fitxer` o un `.zip`.
- Es comprova que el contingut correspongui a l'extensió (un executable canviat de nom a `.docx` es rebutja). Per als fitxers de Packet Tracer només se'n comprova l'extensió.
- Només es permet el bloc `:::model`, com a nota per al professor (p. ex. què ha de tenir el lliurament). No porta `:::clau`, `:::output-*` ni `:::test`.
- Els fitxers no entren a l'informe de possibles còpies.
- L'administrador pot desactivar la pujada de fitxers a *Configuració*. Aleshores els alumnes no en poden pujar, però els ja pujats es conserven.

---

## 7. Exemple complet

````markdown
# Examen UT01 · Xarxes
durada: 60
instruccions: Llegeix cada pregunta atentament. Les respostes en blanc puntuen 0.

---

### Part 1 — Teoria

## 1. [choice] [pts:1] [ra:RA1] [dif:baixa]
Quin port fa servir per defecte un servidor DHCP?

- a) 53
- b) 67
- c) 80
- d) 443

:::model
b
:::

## 2. [choice] [pts:1] [ra:RA1] [dif:mitjana] [ordre:fix]
Quins d'aquests protocols funcionen sobre UDP?

- a) DNS
- b) DHCP
- c) TFTP
- d) Totes les anteriors

:::model
d
:::

## 3. [short] [pts:1.5] [ra:RA2]
Què fa un servidor DHCP?

:::model
Assigna automàticament la configuració de xarxa (adreça IP, màscara, porta d'enllaç i DNS) als equips que s'hi connecten.
:::

:::clau
adreça IP, direcció IP, IP | 0.75
automàtic, automàticament, dinàmic, dinàmicament | 0.75
:::

## 4. [long] [pts:1.5] [ra:RA2] [dif:alta]
Explica com es resol un nom de domini (DNS) des que l'usuari escriu una adreça al navegador.

:::model
El navegador consulta la memòria cau; si no hi és, pregunta al servidor DNS configurat, que resol recursivament preguntant als servidors arrel, als del domini de primer nivell i al servidor autoritatiu, i retorna l'adreça IP.
:::

:::clau
memòria cau, cache, caché
servidor arrel, servidors arrel, root
autoritatiu, autoritari
:::

### Part 2 — Pràctica

## 5. [bash-cmd] [pts:1] [ra:RA3]
Escriu una comanda que mostri els números de l'1 al 5, un per línia.

:::model
seq 1 5
:::

:::output-contains
1
3
5
:::

## 6. [bash-cmd] [pts:1] [ra:RA3]
Escriu una comanda que mostri quants caràcters té la paraula `politecnic` (només el número).

:::model
echo -n politecnic | wc -c
:::

:::output-exact
10
:::

## 7. [bash-cmd] [pts:0.5] [ra:RA3] [dif:baixa]
Escriu una comanda que mostri la data d'avui en format `AAAA-MM-DD`.

:::model
date +%F
:::

:::output-regex
^\d{4}-\d{2}-\d{2}$
:::

## 8. [bash-script] [pts:1.5] [ra:RA3] [dif:mitjana]
Escriu un script que rebi un directori com a paràmetre i mostri `Fitxers: N`, on N és el nombre de fitxers (no directoris) que conté, recursivament.

:::model
#!/bin/bash
echo "Fitxers: $(find "$1" -type f | wc -l)"
:::

:::test
#!/bin/bash
mkdir -p /tmp/prova/sub
touch /tmp/prova/a.txt /tmp/prova/sub/b.txt /tmp/prova/sub/c.txt
[ "$(bash "$SCRIPT_FILE" /tmp/prova 2>/dev/null)" = "Fitxers: 3" ] && exit 0 || exit 1
:::

## 9. [java-prog] [pts:1] [ra:RA4]
Escriu un programa Java que mostri la suma dels números de l'1 al 100.

:::model
public class Main {
    public static void main(String[] args) {
        int suma = 0;
        for (int i = 1; i <= 100; i++) suma += i;
        System.out.println(suma);
    }
}
:::

:::output-exact
5050
:::
````

---

## 8. Motius de rebuig en pujar l'examen

La plataforma revisa tot el fitxer i, si troba un error, el rebutja indicant **la línia** i el motiu. No s'importa res a mitges ni s'ignora cap part en silenci. Els errors més habituals:

- Falta el títol (`# Títol`) o la línia `---` després de la capçalera, o la durada no és un nombre.
- Hi ha text entre la capçalera i la primera pregunta, o sota una secció `###` abans de cap pregunta (sovint és un `###` que s'ha usat com a subtítol dins d'un enunciat).
- Una capçalera de pregunta mal escrita: sense número, sense `[pts:X]`, punts amb coma, etiqueta desconeguda o dificultat no vàlida.
- La suma de punts no és exactament 10, o un tipus de pregunta no existeix.
- Un bloc `:::` sense tancar, repetit, desconegut (p. ex. `:::correct-choice`), amb `:::` dins del text o amb text de l'enunciat després dels blocs.
- Un bloc de codi (```) de l'enunciat sense tancar.
- `choice`: sense opcions, amb una opció fora de `a)`–`d)`, repetida o sense `- ` al davant, o amb una lletra correcta que no és cap de les opcions.
- Un bloc que no correspon al tipus: `:::clau` fora de `text`/`short`/`long`, `:::output-*` fora de les preguntes de codi (també a `html-css`), `:::test` fora de `bash-script`/`ps-script`, dos criteris `:::output-*`, o `:::test` combinat amb `:::output-*`.
- Un `:::output-contains` buit, una `:::output-regex` invàlida o un `:::clau` mal format.
- `[ordre:…]` amb un valor diferent de `fix` o en una pregunta que no és `choice`.
- `[formats:…]` en una pregunta que no és `fitxer`, buit, o amb un format que no és a la llista (p. ex. `exe`, `xlsm`).
- Un bloc `:::` diferent de `:::model` en una pregunta `fitxer`.

Els fitxers desats a Windows (salts de línia CRLF) o amb BOM s'accepten sense problemes. Les línies separadores (`---`, `***`) entre preguntes o seccions s'ignoren.

### Després d'importar: imatges, canvis de text i destinataris

El Markdown només serveix per **crear** l'examen. Un cop importat, no cal tornar-lo a pujar per fer canvis:

- **Imatges:** el Markdown no admet imatges (una línia `![](…)` no mostraria res). S'hi afegeixen des de la plataforma: a **Previsualitzar → ✎ Editar** d'una pregunta, amb el botó «🖼 Afegir imatge» (PNG, JPG, GIF o WebP, màxim 5 MB).
- **Canvis de text:** a **Previsualitzar** es pot editar qualsevol pregunta (enunciat, punts, opcions, resposta correcta, criteris de correcció…), afegir-ne, eliminar-ne i reordenar-les. Només és possible **mentre cap alumne no ha començat l'examen**; després, duplica l'examen per fer-ne una versió nova. Les regles són les mateixes d'aquesta guia, i la suma de punts ha de ser 10 per poder-lo activar.
- **Destinataris:** a l'esborrany, el botó **Destinataris** permet que l'examen sigui només per a alumnes concrets o un grup (p. ex. una recuperació), i un cop actiu s'hi poden afegir més alumnes. Sense triar-ne, és per a tots els matriculats al mòdul.

---

## 9. Instruccions per a la IA que genera l'examen

Quan generis un examen seguint aquesta guia:

- Respon **només amb el contingut del fitxer `.md`**, sense text abans ni després.
- La suma de `[pts:X]` ha de ser **exactament 10**. Usa decimals amb punt.
- Usa només les etiquetes de §4 i els tipus de §6.
- **Tanca cada bloc `:::` abans d'obrir-ne un altre**, cada delimitador en una línia pròpia.
- `choice`: 4 opcions (`- a)` a `- d)`), una sola correcta, la lletra a `:::model`. Si una opció és «Totes les anteriors» o similar, afegeix `[ordre:fix]`.
- `short`, `text` i `long`: inclou `:::model` i un `:::clau` amb 2–4 conceptes imprescindibles, amb els sinònims habituals separats per comes.
- `bash-cmd`, `ps-cmd` i `java-prog`: inclou `:::model` i un criteri `:::output-*` que doni el mateix resultat per a totes les solucions correctes. Prefereix `:::output-contains`; usa `:::output-exact` només si la sortida és totalment determinista.
- `fitxer`: usa-ho només quan l'alumne ha de lliurar un document o una simulació (Word, Excel, Packet Tracer). Indica els formats amb `[formats:…]` i descriu clarament què ha de contenir el lliurament. No hi posis `:::clau`, `:::output-*` ni `:::test`; `:::model` només com a nota per al professor.
- `bash-script` i `ps-script`: inclou `:::model` i un `:::test` robust que creï les seves dades a `/tmp` i comprovi el resultat de manera objectiva. Tria dades de prova amb què **una solució incorrecta però plausible falli** (p. ex. si es demana comptar recursivament, posa fitxers en subdirectoris perquè un `ls` sense recursió doni un altre resultat).
- Tingues en compte l'entorn (§6.6): sense xarxa, només `/tmp` és escrivible, Linux (no Windows), 15 s per execució.
- No usis `###` dins dels enunciats. Per a subtítols, usa negreta.
- No incloguis imatges (`![](…)`): el format no les admet i no es mostrarien. Si una pregunta en necessita una, descriu-ne en el text què hauria de mostrar; el professor l'hi afegirà després des de la plataforma.
- Escriu els enunciats en **català**, llevat que s'indiqui una altra llengua, i no hi incloguis la solució.

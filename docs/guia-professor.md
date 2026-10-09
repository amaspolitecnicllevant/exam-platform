# Guia del professor

Com crear un examen, donar-lo a la classe, corregir-lo i treure'n les notes. Si el que busques és el format dels fitxers
`.md`, mira la [guia de sintaxi](sintaxi-examen-md.md).

Altres guies: [administrador](guia-administrador.md) · [alumne](guia-alumne.md).

---

## 1. En resum

```
Crear l'examen → Revisar-lo → Triar qui el fa → Activar → Seguir-lo al Monitor → Tancar
   → Corregir (proposta + revisió) → Publicar les notes → Estadístiques i exportació
```

Tot el que fas queda restringit als **teus** exàmens: un professor no pot modificar els d'un altre. Si ets administrador, fas el mateix
però veus els de tothom.

---

## 2. Crear un examen

### 2.1 Pujant un fitxer Markdown (`+ Nou examen (MD)`)

Un examen és un fitxer `.md` amb un títol, la durada i les preguntes:

```
# Parcial UT1 · Xarxes
durada: 60
---
### Part 1 — Teoria
## 1. [choice] [pts:2] [ra:RA1]
Quina capa gestiona l'adreçament IP?
- a) Enllaç
- b) Xarxa
- c) Transport
- d) Aplicació
:::model
b
:::
```

- Els punts de totes les preguntes han de **sumar exactament 10**.
- Si el fitxer té algun error, es **rebutja indicant la línia i el motiu**; no s'importa res a mitges.
- La guia de sintaxi (descarregable des de la pàgina *Sintaxi* de l'aplicació) inclou unes **instruccions per a una IA**: pots demanar-li que et
  generi l'examen i enganxar-li la guia perquè respecti el format.

**Tipus de pregunta**

| Tipus | L'alumne… | Es corregeix |
|:--|:--|:--|
| `choice` | tria una opció (a–d) | automàticament, amb la penalització de l'examen |
| `short`, `text`, `long` | escriu una resposta | amb conceptes clau (`:::clau`) la plataforma **proposa** la nota; sense ells, a mà |
| `bash-cmd`, `ps-cmd` | escriu una comanda | s'executa i es compara la sortida (`:::output-*`) |
| `bash-script`, `ps-script` | escriu un script | s'executa amb un test (`:::test`) o amb una sortida esperada |
| `java-prog` | escriu un programa (classe `Main`) | es compila i s'executa; es compara la sortida |
| `html-css` | escriu HTML/CSS, amb previsualització | a mà |
| `fitxer` | **puja un fitxer** (Word, Excel, Packet Tracer, PDF…) | a mà |

### 2.2 Amb l'editor (a *Previsualitzar*)

Mentre l'examen **no tingui cap sessió d'alumne**, pots reescriure qualsevol pregunta, afegir-ne (a qualsevol posició), eliminar-ne i
reordenar-les, sense passar pel fitxer. Cada pregunta passa per les mateixes validacions que la importació.
Pots afegir **imatges a l'enunciat** (PNG, JPG, GIF o WebP, fins a 5 MB).

Quan algun alumne ja l'ha començat, l'examen **queda bloquejat**. Si hi has de fer canvis, **duplica'l**: es crea un esborrany «Còpia de…»
amb les mateixes preguntes, configuració i fitxers de dades.

### 2.3 Preguntes de lliurament de fitxer

```
## 5. [fitxer] [pts:3] [formats:docx,pkt]
Entrega la simulació de Packet Tracer i un document amb la taula d'adreçament.
```

- `[formats:…]` indica què admet la pregunta. Sense l'etiqueta, s'admeten tots els formats de la llista.
- **Formats:** `docx`, `xlsx`, `pptx`, `odt`, `ods`, `odp`, `pdf`, `pkt`, `pka`, `pkz`, `zip`, `png`, `jpg`, `jpeg`, `txt`. Mai documents amb
  macros (`docm`, `xlsm`) ni executables.
- Un fitxer per pregunta, **10 MB com a màxim**. Si en calen diversos, fes diverses preguntes o demana un `.zip`.
- La correcció és **manual**: descarregues el fitxer des de la correcció i hi poses la nota. Mentre no ho facis, la resposta és «pendent de revisar».
- L'administrador pot desactivar la pujada de fitxers per a tothom.

---

## 3. Configurar l'examen

A *Previsualitzar* i a la llista d'exàmens:

| Opció | Efecte |
|:--|:--|
| **Durada** | Es pot canviar mentre és esborrany |
| **Penalització** del test | 0 = no resta; fins a 1 per resposta errada |
| **Una pregunta per pantalla** | L'alumne veu les preguntes d'una en una, amb un índex per saltar a qualsevol (navegació lliure) |
| **Mòdul** | Només el veuen els alumnes **matriculats** en aquest mòdul. Sense mòdul, el veu tothom |
| **Aula** | Només es pot fer des de les màquines de l'aula (per adreça de xarxa) |
| **RA i dificultat** | Per pregunta; permeten veure la nota per resultat d'aprenentatge |
| **Apunts** (`[apunts]`) | L'alumne veu que pot fer servir apunts en paper; l'informe de còpies hi és menys estricte |

---

## 4. Triar qui el fa i activar-lo

### 4.1 Destinataris

A l'esborrany, el botó **Destinataris** et deixa triar **alumnes concrets** (amb cerca per nom) o **un grup sencer**. És el cas típic d'una
**recuperació**: l'examen només el veuran els que triïs, la resta de la classe no el veurà mai. Sense triar-ne cap (o amb «Tornar a Tots»),
és per a tots els matriculats al mòdul.

Un cop actiu, **Alumnes** permet **afegir-ne més en qualsevol moment** (per exemple, qui el fa un altre dia: el seu temps comença quan
l'obre) o treure els que encara no l'han obert.

### 4.2 Activar (publicar)

**Activar** fa que l'examen sigui visible i es pugui fer. **Desactivar** el torna a esborrany. **Tancar** l'acaba: els que el feien
l'entreguen tal com el tenen, i ja no es pot desar res més.

**Desactivar o tancar no amaga les dades.** Un examen desactivat o tancat que ja té alumnes continua mostrant **Correccions**,
**Estadístiques**, **Exportar** i els botons de publicar o ocultar notes: pots seguir mirant les respostes i exportar-les igualment.
(Un esborrany nou, sense cap alumne, no mostra aquests botons perquè no hi ha res a veure.)

### 4.3 Programar

**Programar** (grup, data i hora) activa l'examen sol a l'hora indicada. Els alumnes poden començar-lo durant uns minuts des de l'hora
d'inici (20 per defecte). Un examen programat és per al grup; no es pot combinar amb destinataris concrets.

### 4.4 Assignar a un grup

En un examen actiu, **Assignar a grup** crea una sessió per a cada alumne del grup que encara no en tingui. El selector de grups té
filtres de departament, cicle i mòdul, i s'obre ja filtrat pel mòdul de l'examen perquè no el donis per error a un altre mòdul.

---

## 5. Mentre l'examen és en marxa

El **Monitor** s'actualitza cada 15 segons i mostra, per a cada alumne:

- el nom, el correu i l'**adreça IP** des d'on fa l'examen;
- quantes respostes ha desat de les totals;
- les **pèrdues de focus** (canvis de pestanya o de finestra, i intents d'obrir una segona pestanya) i un avís si arriben al llindar que ha fixat l'administrador;
- el temps que porta, i els que ja han entregat.

Si algú entrega per error, **Reiniciar sessió** el deixa tornar a continuar (conserva les respostes).

**Una sola pestanya.** Durant l'examen, l'alumne només pot tenir oberta **una** pestanya de la plataforma. Si n'obre una segona (una altra
còpia de l'examen o qualsevol altra pàgina de la plataforma), aquesta es **bloqueja** i a l'examen se li suma una pèrdua de focus, que
tu veus al Monitor. Limitacions: la plataforma no pot veure ni impedir què hi ha obert fora d'ella (una altra aplicació, o una altra web
en una altra pestanya). Només queda registrat quan l'alumne canvia d'una a l'altra. Per garantir «només el navegador de l'examen» cal
un navegador d'examen tancat (com Safe Exam Browser) o un mode quiosc als ordinadors. El control requereix HTTPS (o `localhost`); sense
HTTPS només hi ha el registre de pèrdues de focus.

---

## 6. Corregir

### 6.1 La plataforma proposa, tu decideixes

| Tipus | Què fa en entregar |
|:--|:--|
| Test | Corregeix sol, amb la penalització |
| Text amb conceptes clau | Proposa la nota i diu què falta (`−0,5: no esmenta «DNS»`) |
| Codi | L'executa en segon pla i proposa la nota amb els motius (error, temps esgotat, línies que falten…) |
| Lliurament de fitxer, HTML/CSS, text sense conceptes clau | Res: es corregeix a mà |

Les notes proposades són **provisionals**: compten al total, però la resposta queda «pendent de revisar» fins que l'**acceptes** (una, per
alumne o totes) o hi poses una nota manual. **No es poden publicar les notes mentre hi hagi respostes pendents.**

### 6.2 Dues maneres de corregir

- **Per alumne:** totes les respostes d'un alumne, amb filtres (no entregats, amb pendents, revisats) i cerca per nom.
- **Per pregunta:** totes les respostes d'una pregunta juntes, per mantenir el mateix criteri. La resposta correcta i els conceptes clau surten un cop a dalt.

**Amb el teclat:** a la casella de nota, **Intro** desa i passa a la resposta següent; amb la casella buida, **Intro accepta la proposta**.
S'accepta la coma decimal.

### 6.3 Altres eines

- **Comentari per resposta:** l'alumne el veu quan publiques les notes.
- **Bonus:** marcar una pregunta com a bonus fa que tothom la tingui bé (punts sencers), l'hagi respost o no. Es pot desfer.
- **Canviar la resposta correcta** d'una pregunta de test (si t'has equivocat): es recorregeix tot sol.
- **Executar** una resposta de codi a mà, per veure què fa.
- **Lliuraments de fitxer:** botó *Descarregar* a la resposta.
- **Revisada amb IA:** les respostes a les quals has aplicat una revisió amb IA (vegeu §7.3) ho indiquen: la nota d'abans, la nova i la justificació de la IA. Només ho veus tu; l'alumne no.

### 6.4 Publicar les notes

Fins que no **publiques les notes**, l'alumne només veu «pendent de correcció» i el servidor no li envia ni puntuacions ni motius. Pots tornar-les a ocultar.

---

## 7. Després de corregir

### 7.1 Estadístiques

Entregats, mitjana, mediana, mínima i màxima, % d'aprovats i histograma. Per pregunta: rendiment, % d'encerts i opcions triades (test) i respostes
en blanc; les que tenen un rendiment inferior al 40 % es marquen. Es pot imprimir o desar en PDF.

### 7.2 Possibles còpies

L'informe mostra parelles d'alumnes amb respostes molt semblants (text, scripts, Java, HTML) i errades de test coincidents, amb els fragments
ressaltats. **És un indici, no una prova.** No compara respostes curtes ni comandes d'una línia, i descompta el que coincideix amb l'enunciat o amb
la resposta correcta. En preguntes amb apunts el llindar és més alt.

### 7.3 Exportar

El menú **Exportar** de cada examen ofereix:

| Exportació | Per a què |
|:--|:--|
| **Notes per alumne (CSV)** | Passar les notes al full de qualificacions. S'obre a Excel en català |
| **Notes per RA (CSV)** | Actes i programació |
| **Excel (.xlsx)** | Notes, notes per RA i respostes en un sol fitxer |
| **Informe (.md)** | Estadístiques per pregunta i les més difícils |
| **Fitxers lliurats (ZIP)** | Un directori per alumne, per corregir els lliuraments fora de línia |
| **Respostes per a una IA (.md)** | Vegeu més avall |

**Revisar amb una IA sense donar dades personals.** *Respostes anònimes* genera un `.md` on els alumnes surten com «Alumne 7F3A2C» (un codi
estable) i hi afegeix les instruccions per a qui revisa, la resposta model i el **format exacte** en què ha de respondre. **Clau d'alumnes**
(CSV) lliga cada codi amb l'alumne real: **no enviïs aquest fitxer a la IA**. Si un alumne escriu el seu nom dins d'una resposta, apareixerà
al fitxer. Només s'inclouen noms i correus si ho demanes explícitament. Cada exportació queda al registre d'activitat.

**Importar la revisió de la IA.** El flux sencer:

1. A **Exportar**, descarrega *Respostes anònimes (.md)* i passa'l a la IA amb un missatge curt, per exemple: *«Fes la revisió que descriu el
   document adjunt. Respon només amb el bloc CSV demanat a les instruccions, sense text addicional.»* Si vols criteris propis (més estricte,
   no penalitzar l'ortografia…), afegeix-los en aquest missatge.
2. La IA respon amb un bloc CSV: `alumne;pregunta;nota;justificacio`, una fila per alumne i pregunta.
3. A **Correccions**, prem **Importar revisió IA** i enganxa la resposta (o puja el fitxer). L'aplicació torna a lligar cada codi amb
   l'alumne real **sola**, sense necessitat de la clau d'alumnes, i et mostra el nom real al costat de cada nota perquè ho comprovis.
4. **Previsualització: no es desa res.** Veus, per a cada resposta, la nota actual, la nova, la diferència i la justificació, i l'efecte a
   la nota final de cada alumne. Marques què vols aplicar; les files que **substituirien una nota que ja havies revisat** surten en groc i
   **desmarcades**. Les files que no es poden aplicar (alumne o pregunta desconeguts, nota fora de rang, files repetides) es llisten amb el motiu.
5. **Aplicar** desa les files marcades com a **nota revisada**, i guarda la nota anterior i la justificació de la IA (només per a tu: la
   justificació **no** va al comentari de l'alumne). Si entre la previsualització i l'aplicació has canviat una nota, aquella fila se salta.

Només es poden canviar les respostes que corregeixes tu: **no** les de test (les corregeix el sistema), ni els lliuraments de fitxer
(no s'envien a la IA), ni les preguntes anul·lades, ni les d'alumnes que no han entregat o no han respost. Si les notes ja són visibles per
als alumnes, la previsualització t'ho avisa, perquè els canvis els afectarien. Cada aplicació queda al registre d'activitat (`EXAM_REVISIO_IA`).

### 7.4 Recuperacions

A *Estadístiques*, **Crear grup amb els suspesos** llista els suspesos i els no presentats, tries els que vols i es crea un grup del mòdul. Després
**duplica l'examen**, canvia'n el títol i dona'l a aquest grup (amb Destinataris, o programant-lo). Si algú ha de fer la recuperació sense haver fet
l'examen (convalidació, baixa justificada), afegeix-lo al grup a mà.

---

## 8. Grups i alumnes

- **Grups:** conjunts d'alumnes, amb un mòdul opcional. Un alumne pot ser a molts grups. Pots tenir un grup per a tothom i un altre només per als de la recuperació.
- **Afegir alumnes a un grup:** cerca per nom o correu i filtres de cicle, mòdul i curs. S'obre filtrat pel mòdul del grup i amb el curs més recent;
  **Marcar els N visibles** els selecciona tots. Els alumnes sense matrícula només surten si treus els filtres.
- **Pestanya Alumnes:** tots els alumnes, filtrables també per grup o «sense grup»; des d'aquí pots assignar els grups d'un alumne.
- **Links de convit:** un link per a un mòdul i un curs, amb data de caducitat i nombre d'usos opcionals. El comparteixes per Classroom, WhatsApp, etc.
  Qui no tingui compte, es registra i queda matriculat; qui ja en tingui, queda matriculat. Els correus de professors i administradors es rebutgen.

---

## 9. Preguntes freqüents

| Pregunta | Resposta |
|:--|:--|
| No puc editar l'examen | Ja té sessions d'alumne. **Duplica'l** i edita la còpia |
| No puc publicar les notes | Hi ha respostes «pendents de revisar». Accepta les propostes o posa'ls nota |
| No puc activar l'examen | Els punts no sumen 10, o és un examen restringit sense cap destinatari |
| Un alumne no veu l'examen | Comprova: que estigui actiu, que sigui del mòdul on està matriculat, que sigui un dels destinataris (si és restringit) i, si té aula, que faci l'examen des d'allà |
| Un alumne el fa un altre dia | Afegeix-lo a **Alumnes** de l'examen actiu: el seu temps comença quan l'obre |
| Un alumne diu que no li deixa pujar un fitxer | Format no admès a la pregunta, fitxer de més de 10 MB, o l'administrador ha desactivat la pujada |
| La nota d'un alumne és «provisional» | Té respostes pendents; compten 0 fins que les revisis |
| Veig un examen que no he creat | Els exàmens dels mòduls que imparteixes surten al llistat. El poden gestionar i veure'n els resultats: qui l'ha creat, els professors del mòdul i els administradors. Cap altre professor |
| Un examen desactivat no té els botons de Correccions i Exportar | Només es mostren si l'examen ja té alumnes. Si en té i no surten, recarrega la pàgina (Ctrl+F5) |
| En importar la revisió diu «No s'ha trobat cap fila» | La IA no ha respost amb el format demanat. Torna-li a dir: *«Respon només amb el bloc CSV, amb la capçalera alumne;pregunta;nota;justificacio»* |
| Una fila de la importació diu «Alumne no reconegut» | La IA ha canviat o inventat el codi. Si només n'ha canviat algun, corregeix-lo al text abans de tornar a previsualitzar |

# SEDEX — Funcionalitats per rol

> Document viu. S'actualitza quan el producte canvia. Si hi ha contradiccions, s'indiquen al final de cada secció afectada.

---

## Rols del sistema

| Rol | Descripció |
|-----|-----------|
| **ADMIN** | Gestió global del sistema. Pot fer tot el que fan els professors i a més gestiona l'estructura acadèmica. |
| **PROFESSOR** | Crea i gestiona els seus propis exàmens. Accés als seus grups i alumnes. |
| **STUDENT** | Fa exàmens publicats. Veu els seus resultats. |

---

## ADMIN

### Gestió d'usuaris
- Llistar tots els usuaris del sistema.
- Crear usuaris manualment (nom, email, password, rol).
- **Importar alumnes des d'un CSV** (també els professors, només alumnes dels mòduls que imparteixen):
  columnes `nom`, `email` i, opcionals, `contrasenya`, `rol`, `modul` (codi), `curs` i `grup`, amb capçalera en
  català o anglès, separades per comes o punt i coma (Excel), en UTF-8 o Windows-1252. Contrasenya en blanc →
  se'n genera una i es poden descarregar totes en acabar (només es mostren una vegada). Amb `modul`, l'alumne
  queda matriculat (curs actiu per defecte); amb `grup`, s'hi afegeix i, si el grup no existeix, es crea. Si el
  correu ja té compte no es modifica: només es matricula o s'afegeix al grup. Plantilla descarregable.
- Eliminar usuaris (no si té exàmens fets o creats; ningú no es pot eliminar a si mateix).

### Estructura acadèmica
- **Departaments**: crear, llistar, editar, esborrar.
  - Assignar professors a un departament (amb flag `esCap`). Un professor pot pertànyer a **més d'un** departament.
  - Treure professors d'un departament.
- **Cicles**: crear, llistar, editar, esborrar. Cada cicle pertany a un departament.
- **Mòduls**: crear, llistar, editar, esborrar. Cada mòdul pertany a un cicle.
  - Gestió d'imparticions: quin professor imparteix quin mòdul en quin curs acadèmic.

### Matrícules
- Llistar matrícules per mòdul i curs acadèmic.
- Matricular alumnes en un mòdul per un curs concret, **molts alhora** (selecció múltiple amb cerca i "Tots").
- Esborrar una matrícula.

> **Nota**: La matrícula és per mòdul, no per cicle. Un alumne pot estar matriculat en mòduls de cicles diferents.

### Còpies de seguretat
- Còpia automàtica diària de la base de dades i dels fitxers de dades (30 dies de retenció), verificada en fer-la.
- A Configuració es veu l'estat de l'última còpia; si falla o fa més de 26 hores, tots els administradors veuen un avís a dalt de cada pàgina.
- Scripts per fer una còpia ara, comprovar-ne una sense tocar res i restaurar (vegeu `infra/backup/`).

### Espai ocupat pels exàmens
- A *Espai ocupat* s'audita l'espai dels fitxers (adjunts a les preguntes i lliuraments dels alumnes) **per professor, departament, cicle, mòdul i examen**, amb recompte de fitxers i barra de proporció. Els exàmens sense mòdul surten a part.
- Mostra l'espai lliure i total del disc del servidor i el compara amb el que registra la base de dades: avisa si hi ha fitxers orfes (més al disc que a la BD) o si en falten.
- A *Configuració* hi ha l'interruptor **Permet als alumnes pujar fitxers**: desactivat, ningú no en pot pujar, però els ja pujats es conserven i es poden descarregar.

### Aules
- Crear aules amb nom únic i rang d'adreces de xarxa en format **CIDR** (p.ex. `10.0.1.0/24`).
- Editar i esborrar aules.
- Les aules defineixen quines màquines poden fer un examen quan l'examen té restricció d'aula.

---

## PROFESSOR

### Exàmens
- Crear un examen a partir d'un fitxer Markdown (`.md`) estructurat.
- Llistar els propis exàmens, amb cerca per títol i filtres per estat i mòdul.
- Editar el títol i, en esborrany, la durada (a Previsualitzar).
- **Duplicar** un examen: crea un esborrany "Còpia de …" amb les mateixes preguntes, configuració i fitxers de dades (sense la programació, les notes visibles ni els bonus).
- Previsualitzar un examen.
- **Publicar** un examen (DRAFT → PUBLISHED): l'examen es fa visible als alumnes.
- **Desactivar** un examen (PUBLISHED → DRAFT).
- **Tancar** un examen (→ CLOSED): es poden exportar resultats. Els alumnes que l'estaven fent l'entreguen tal com el tenien; a partir d'aquí (i també si es desactiva) ja no es pot desar ni executar codi.
- Eliminar un examen.

#### Configuració de l'examen
- Ajustar el factor de penalització per resposta errada en preguntes de selecció múltiple (0 = sense penalització, fins a 1).
- Activar el mode **una pregunta per pantalla** (des de la previsualització): l'alumne veu les preguntes d'una en una, amb un índex per saltar a qualsevol pregunta i botons Anterior/Següent (navegació lliure). Desactivat per defecte.
- Assignar un **mòdul**: l'examen només és visible als alumnes matriculats en aquell mòdul. Sense mòdul, és visible a tothom.
- Assignar una **aula**: l'examen només es pot fer des de màquines de la subxarxa CIDR de l'aula. Sense aula, sense restricció d'IP.
- **Programar** un examen (data, hora i grup): l'examen s'activa automàticament a l'hora indicada (hora local del navegador; internament es desa en UTC). Només hi ha conflicte si se solapa amb un altre examen programat del mateix grup.
- Anul·lar la programació.

#### Preguntes
- Modificar la resposta correcta d'una pregunta de tipus CHOICE (correcció posterior a la realització).
- Marcar una pregunta com a **bonus** durant la correcció: tothom la té bé (punts sencers), l'hagi resposta o no, encara que ja s'hagués puntuat. Es pot desfer ("Treure bonus") i torna a comptar la nota de cada alumne.
- Cada canvi de resposta correcta o de bonus queda a l'auditoria amb el valor anterior i el nou ("resposta correcta: a → c") i el nombre de respostes re-corregides.
- Assignar o editar el **RA (Resultat d'Aprenentatge)** i la **dificultat** (baixa/mitjana/alta) de cada pregunta, tant des del Markdown (`[ra:RA1] [dif:alta]`) com des de la previsualització de l'examen (edició inline per pregunta).
- Veure un resum de distribució de preguntes per RA i per dificultat a la previsualització.
- Preguntes de **lliurament de fitxer** (`[fitxer]` amb `[formats:docx,xlsx,pkt]`): l'alumne puja un document Word o Excel, una simulació de Packet Tracer, un PDF… (un fitxer per pregunta, màxim 10 MB, sense macros ni executables). Es corregeixen a mà: el professor descarrega el fitxer des de la correcció i hi posa la nota; mentre no la posi, la resposta és «pendent de revisar».

#### Exportacions
- El menú **Exportar** de cada examen ofereix: notes per alumne (CSV), notes per resultat d'aprenentatge (CSV), Excel amb notes, RA i respostes, informe de l'examen (`.md`) amb estadístiques per pregunta, **ZIP amb els fitxers lliurats** (un directori per alumne) i el CSV detallat d'una fila per resposta.
- **Respostes per a una IA** (`.md`): anònimes per defecte (els alumnes surten com «Alumne 7F3A2C», un codi estable derivat de la sessió), amb les instruccions per a qui revisa i la resposta model. El fitxer de **claus** (CSV) a part lliga cada codi amb l'alumne real, per tornar a posar les notes. Només es pot incloure el nom i el correu si es demana explícitament.
- Els CSV s'obren a Excel en català (`;`, coma decimal, UTF-8). Cap text d'alumne s'interpreta com a fórmula.
- Cada exportació queda a l'auditoria (`EXAM_EXPORTED`), i les que porten noms hi consten com a tals.

#### Correcció assistida
- **Test (CHOICE)**: es corregeix automàticament en entregar, amb el factor de penalització de l'examen.
- **Preguntes de text** amb bloc `:::clau`: en entregar, la plataforma proposa una nota segons els conceptes clau presents i indica els motius de penalització (p. ex. `−0,5: no esmenta «DNS»`).
- **Preguntes de codi**: s'executen automàticament en segon pla en entregar; la proposta indica els motius (error d'execució, timeout, línies que falten a la sortida…). Amb `:::output-contains` la proposta és proporcional a les línies trobades.
- La nota proposada és **provisional**: compta al total però la resposta queda "pendent de revisar" fins que el professor l'accepta (una, per alumne o totes) o hi posa una nota manual.
- A la correcció es veu la resposta correcta (`:::model`) i els conceptes clau al costat de la resposta de l'alumne.
- **No es poden publicar les notes** mentre hi hagi respostes pendents de revisar.
- **Corregir per pregunta**: totes les respostes d'una pregunta juntes (per calibrar la correcció), amb filtre pendents/totes i navegació entre preguntes. La resposta correcta i els conceptes clau surten un sol cop a dalt.
- **Filtres per alumne**: cerca per nom; tots / amb pendents / revisats / no entregats; al detall, "només les respostes pendents".
- **Correcció amb el teclat**: a la casella de nota, Intro desa i passa a la resposta següent; amb la casella buida, Intro accepta la proposta. S'accepta la coma decimal.
- **Comentari per resposta**: el professor pot deixar un comentari a cada resposta; l'alumne el veu quan es publiquen les notes. Queda a l'auditoria i al CSV.

#### Estadístiques
- Pàgina d'estadístiques per examen (des de la llista d'exàmens o la correcció): entregats, mitjana, mediana, mínima–màxima, % d'aprovats i histograma de notes.
- Per pregunta: rendiment mitjà, % d'encerts i opcions triades (test), respostes en blanc; es pot ordenar per trobar les preguntes més difícils (es marquen amb rendiment < 40 %).
- Es pot imprimir o desar en PDF des del navegador.

#### Recuperació
- A Estadístiques, **Crear grup amb els suspesos**: llista els suspesos (nota sobre 10) i els no presentats, es trien i es crea un grup del mòdul.
- Després es duplica l'examen, se li canvia el títol i es programa per a aquest grup. Un examen programat per a un grup només el poden fer els alumnes del grup.

#### Possibles còpies
- Informe per examen (des de la correcció) amb els parells d'alumnes amb respostes molt semblants (text, scripts, Java, HTML) i amb errades de test coincidents; respostes costat per costat amb els fragments coincidents ressaltats.
- No compara les respostes curtes ni les comandes d'una línia, i descompta el que coincideix amb l'enunciat, la resposta correcta o la resta de la classe. Llindar configurable per l'administrador. És un indici, no una prova.
- Preguntes **amb apunts** (p. ex. la part pràctica): es marquen amb `[apunts]` a la secció o a la pregunta, o des de la previsualització. L'informe hi aplica un llindar més alt (configurable, 95 % per defecte) i les etiqueta; l'alumne veu que hi pot fer servir apunts.
- Al monitor, avís dels alumnes que arriben al llindar de pèrdues de focus configurat.

#### Visibilitat de notes
- **Publicar notes als alumnes**: un cop corregit l'examen, el professor activa la visibilitat de les notes. Fins aleshores, l'alumne veu "pendent de correcció" i el servidor no li envia ni puntuacions ni motius.
- **Ocultar notes**: el professor pot tornar a ocultar-les.

#### Gestió de sessions
- **Reiniciar sessió** d'un alumne des del Monitor: la sessió torna a IN_PROGRESS (conservant les respostes), permetent a l'alumne continuar o modificar.
- **Reactivar la finestra d'examen** (reopen window): obre de nou la finestra temporal per a tots els alumnes. Només en exàmens programats (els que no ho estan no tenen finestra).

### Grups i alumnes
- Crear grups d'alumnes.
- Afegir/treure alumnes d'un grup.
- Assignar un mòdul a un grup (filtre de qui pot participar en exàmens d'aquell mòdul).
- Assignar un examen publicat a un grup (crea sessions individuals per als alumnes elegibles).
- Cerca i **filtres** per triar alumnes i grups: en assignar alumnes a un grup (nom o correu, cicle, mòdul i curs, amb «Marcar els visibles»), a la pestanya Alumnes (també per grup o «sense grup») i en triar el grup d'un examen (departament, cicle i mòdul). La llista d'exàmens es filtra per títol, estat, professor, cicle, mòdul, grup programat i dates. Un professor només veu les matrícules dels mòduls que imparteix.

### Links de convit (invitació d'alumnes)
- Generar un **link de convit** per a un mòdul i curs acadèmic concret.
  - Paràmetres opcionals: data d'expiració, nombre màxim d'usos.
- Copiar el link (es comparteix per Classroom, WhatsApp, etc., sense necessitat de servidor SMTP).
- Desactivar un link.
- Veure l'historial dels links generats.
- Filtrar els links per mòdul i curs.

> Quan un alumne accedeix al link:
> - Si **no té compte**, pot registrar-se (nom, email, password) i queda automàticament matriculat al mòdul.
> - Si **ja té compte i és STUDENT**, queda matriculat al mòdul (si no ho estava ja).
> - Si l'email pertany a un PROFESSOR o ADMIN, el link és rebutjat.

### Monitor en temps real
- Veure en temps real (actualització automàtica cada 15 s) tots els alumnes que estan fent un examen publicat:
  - Nom i email de l'alumne.
  - Adreça IP des de la qual fa l'examen.
  - Nombre de respostes guardades vs. total de preguntes.
  - Nombre de pèrdues de focus detectades (canvi de pestanya/finestra).
  - Temps transcorregut des de l'inici.
- Veure els alumnes que ja han entregat, amb el temps que fa que van entregar.

### Correccions
- Veure totes les sessions d'un examen (alumnes, respostes, puntuació automàtica).
- Re-corregir preguntes (després de marcar un bonus o canviar la resposta correcta).
- Veure badges de RA i dificultat a la llista de preguntes.
- Al detall de cada alumne: desglossament de nota per RA (punts obtinguts vs. possibles per cada RA).

---

## STUDENT

### Historial i notes
- Pàgina "Historial i notes": tots els exàmens entregats amb data i nota (només quan el professor l'ha publicada), agrupats per mòdul.
- Per mòdul: nota mitjana i gràfic d'evolució de les notes; resum global (entregats, mitjana, aprovats).

### Exàmens disponibles
- Veure els exàmens publicats als quals té accés (per grup assignat o per mòdul matriculat), agrupats per mòdul.
- Veure els exàmens passats (entregats), agrupats per mòdul, amb la data de realització i l'estat (notes disponibles / pendent de correcció).
- Iniciar o reprendre una sessió d'examen.
- **Tornar a fer un examen ja entregat**: si l'alumne ho confirma, s'esborren les respostes anteriors i comença una nova sessió.

> **Restriccions d'accés a l'examen**:
> - Si l'examen té **aula** assignada, la IP de la màquina des d'on es connecta ha d'estar dins el rang CIDR de l'aula. En cas contrari, rep un error 403 amb el missatge explicatiu.

### Realització de l'examen
- El **nom i email de l'alumne** es mostren a la capçalera de l'examen (mesura anti-suplantació).
- Respondre preguntes de tipus: TEXT, SHORT, LONG, CHOICE (selecció múltiple), BASH_CMD, PS_CMD, BASH_SCRIPT, PS_SCRIPT.
- Executar comandes/scripts directament des del navegador (tipus BASH/PS).
- **Pujar un fitxer** a les preguntes de lliurament (Word, Excel, Packet Tracer…): es comprova el format i la mida, es pot substituir o esborrar fins a entregar l'examen, i es pot tornar a descarregar.
- Les respostes es guarden automàticament amb un debounce de 500 ms.
- Compte enrere visible en una barra fixa a dalt, amb l'estat del desament (pendent, desant, desat, error). Avisos quan queden 10 minuts i 1 minut. En arribar a 0, l'examen s'entrega automàticament.
- Les preguntes es barregen de forma determinista per sessió (diferent ordre per a cada alumne, però consistent si es reprèn).
- Cada canvi de pestanya/finestra queda registrat com a pèrdua de focus.
- Entregar l'examen manualment (confirmació requerida).

### Resultats
- Veure el resultat de la sessió un cop entregada (puntuació automàtica de CHOICE, indicació de preguntes amb bonus).
- Desglossament de nota per RA (quan les notes estan publicades i hi ha preguntes amb RA assignat).
- Si les notes **no estan publicades**, l'alumne veu "pendent de correcció" i no pot accedir a les puntuacions.

---

## Seguretat i aïllament
- Autenticació per **JWT** (login amb email+password) o **Google OAuth2**.
- Tots els endpoints filtren per propietat: un professor no pot modificar exàmens d'un altre professor. Un alumne no pot accedir a sessions d'un altre alumne.
- La comprovació d'IP (aula) es fa al servidor, no al client.
- Les invitacions rejeten emails de professors i admins.

---

## Registre d'auditoria
Els següents esdeveniments queden registrats a la base de dades:
- `EXAM_CREATED`, `EXAM_PUBLISHED`, `EXAM_CLOSED`, `EXAM_DELETED`
- `EXAM_SUBMITTED` (per sessió d'alumne)
- `EXAM_EXPORTED` (qualsevol exportació d'un examen)

---

## Integracions externes (fora de la Fase 1)
- Google Drive (emmagatzematge de documents generats)
- Google Classroom (publicació d'exàmens i recollida d'entregues)

---

## Contradiccions i ambigüitats obertes

*(Cap contradicció detectada fins ara. S'actualitzarà quan n'aparegui alguna.)*

---

## Mesures contra la còpia entre alumnes

- L'ordre de les preguntes es barreja per a cada alumne dins de cada secció.
- L'ordre de les opcions de les preguntes de test es barreja per a cada alumne (i les lletres es reassignen), de manera que dir "la B" no serveix. Es desa la lletra original: la correcció no canvia. Es pot desactivar per pregunta (`[ordre:fix]` al Markdown o la casella de la previsualització), p. ex. si una opció és «Totes les anteriors».
- Opcional per examen: una pregunta per pantalla.
- Registre de pèrdues de focus i restricció per aula (xarxa CIDR).

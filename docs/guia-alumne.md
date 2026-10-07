# Guia de l'alumne

Què has de saber per fer un examen a la plataforma: com entrar, com funciona la pantalla, què es guarda i què passa quan acabes.

Altres guies: [professor](guia-professor.md) · [administrador](guia-administrador.md).

---

## 1. Abans d'un examen

### Entrar

1. Obre al navegador l'adreça que t'ha donat el professor (per exemple `https://examens.centre.cat:3443`).
2. Escriu el **correu i la contrasenya**.
3. Si el professor t'ha donat un **link de convit** i encara no tens compte, el link et deixa registrar-te (nom, correu i contrasenya) i
   et matricula al mòdul. Si ja en tens, només et matricula.

**Canvia la contrasenya** la primera vegada, amb el botó de la barra lateral. Fes-la llarga i no la comparteixis: tot el que es faci amb el
teu compte compta com a fet per tu.

> **Si el navegador avisa que la connexió no és segura:** no continuïs i digues-ho al professor o a informàtica. És un problema d'instal·lació del
> certificat al teu ordinador, no hauries d'ignorar l'avís.

### Els teus exàmens

A **Els meus exàmens** hi ha, agrupats per mòdul:

- els exàmens que pots fer ara;
- els que ja has entregat, amb la data i si la nota ja està disponible.

Només hi surten els exàmens del teu mòdul o els que el professor t'ha assignat. Si no veus el que t'espera, mira l'apartat 7.

---

## 2. Fer l'examen

### Començar

Prem l'examen i comença el **compte enrere**, que veus sempre en una barra fixa a dalt. Fixa't en tres coses:

- A la capçalera hi surt **el teu nom i el teu correu**.
- Alguns exàmens només es poden fer **des d'una aula** concreta. Si no hi ets, el sistema no et deixarà entrar.
- Si l'examen està programat, pots començar durant els primers minuts des de l'hora d'inici. Després ja no.

### Les preguntes

| Tipus | Què has de fer |
|:--|:--|
| **Test** | Tria una opció. Les opcions poden sortir en un ordre diferent del dels teus companys |
| **Resposta curta o llarga** | Escriu la resposta al quadre |
| **Comanda o script** (bash, PowerShell) | Escriu el codi i prem **▶ Executar** per veure què fa |
| **Programa Java** | La classe s'ha de dir `Main`. **▶ Executar** el compila i l'executa |
| **HTML/CSS** | Escriu el codi i en pots veure la previsualització |
| **Lliurament de fitxer** | **Puja un fitxer** (vegeu l'apartat 3) |

- Algunes preguntes porten l'etiqueta **«Pots fer servir apunts»**: només en aquestes. Si no hi és, no en pots fer servir.
- Si l'examen és **d'una pregunta per pantalla**, hi ha un índex per saltar a qualsevol pregunta i els botons *Anterior* i *Següent*.
- Algunes preguntes porten fitxers adjunts (dades): els pots descarregar des de la mateixa pregunta.
- Un codi que s'executa té un **temps màxim** i **no té accés a internet**. Si falla, llegeix el missatge: sovint diu què passa.

### El desament és automàtic

El que escrius es **desa sol** al cap d'un instant. A dalt veus l'estat: *pendent*, *desant*, *desat* o *error*.

- Si veus **error**, **aquella resposta potser no s'ha desat**: pot ser que hagis perdut la connexió. Quan torni, **toca la resposta
  de nou** (per exemple, afegeix i esborra un espai) perquè es torni a desar, i **no entreguis fins que vegis «desat»**.
- No cal prémer cap botó per desar. Sí que has de **prémer Entregar** quan acabis.
- Si et caigués el navegador o l'ordinador, **torna a entrar i continua**: les respostes desades hi són i el temps no s'ha parat.

### El temps

Quan queden **10 minuts** i **1 minut** surt un avís. En arribar a zero, **l'examen s'entrega sol** amb el que tinguis.

### Què es registra

Per garantir que l'examen és just, el sistema guarda:

- cada vegada que **canvies de pestanya o de finestra** (el professor ho veu, i si passa massa vegades s'avisa);
- l'**adreça** des d'on el fas;
- després de l'examen, es poden **comparar les respostes** entre alumnes per detectar còpies. Si dues respostes són molt semblants, el professor ho revisa.

Fes servir només el que el professor et permet, i no comparteixis respostes.

---

## 3. Pujar un fitxer

Algunes preguntes demanen un document (Word, Excel, una simulació de Packet Tracer, un PDF…):

1. La pregunta diu quins formats admet (per exemple `.docx i .pkt`) i la mida màxima, que és de **10 MB**.
2. Prem **Triar un fitxer** i escull-lo.
3. Quan acaba, veus el nom, la mida i **✓ Pujat**. Pots **descarregar-lo** per comprovar que és el bo.
4. Si te n'adones d'un error, prem **Substituir el fitxer** (el nou reemplaça l'anterior) o **Esborrar**.

Només pots canviar-lo **fins que entregues l'examen**. Si el fitxer no passa, el missatge t'ho diu:

| Missatge | Què passa |
|:--|:--|
| «Aquesta pregunta només admet fitxers .docx, .pkt» | El format no és el que demana la pregunta |
| «El fitxer supera la mida màxima de 10 MB» | És massa gran: redueix-lo (imatges més petites, per exemple) |
| «El contingut no correspon a un fitxer .docx» | Has canviat l'extensió d'un fitxer d'un altre tipus, o està malmès. Torna'l a desar des del programa |
| «La pujada de fitxers està desactivada» | L'administrador l'ha desactivat: avisa el professor |

---

## 4. Entregar

1. Prem **Entregar**. Si tens preguntes sense resposta, t'ho diu.
2. Confirma. **Després no podràs canviar les respostes.**
3. Veuràs la pantalla de resultats.

**Si has entregat per error:** demana al professor que et reiniciï la sessió; les teves respostes es conserven.

**Tornar a fer un examen que ja has entregat:** només és possible mentre l'examen continua obert, **no s'han publicat les notes** i encara
queda temps. Si ho confirmes, **s'esborren les respostes anteriors** i en comences una de nova.

---

## 5. Després de l'examen

- Mentre el professor no hagi publicat les notes, veuràs **«pendent de correcció»**. No és que no s'hagi guardat: encara no ho ha corregit.
- Quan les publica, veus la **nota**, els punts de cada pregunta, els **comentaris** del professor i, si hi ha resultats d'aprenentatge, la nota per cadascun.
- A **Historial i notes** hi ha tots els teus exàmens, per mòdul, amb la mitjana i l'evolució.
- Les preguntes que es corregeixen a mà (lliuraments, HTML/CSS) poden tardar més que el test.

---

## 6. Consells

- **Arriba amb temps** i comprova que veus l'examen abans que comenci.
- **No tanquis la pestanya de l'examen** fins que hagis entregat. I no facis altres coses al mateix navegador: els canvis de pestanya queden registrats.
- **Llegeix bé la pregunta**: en una de codi, el resultat ha de ser exactament el que demana.
- **Desa el fitxer abans de pujar-lo** i no el tinguis obert si el programa el bloqueja.
- **Si alguna cosa va malament, avisa el professor al moment**, sense sortir de l'examen.

---

## 7. Si alguna cosa falla

| Què passa | Què fer |
|:--|:--|
| No veig l'examen | Comprova que estàs matriculat al mòdul i que el professor l'ha activat. Si és una recuperació, ha d'haver-te afegit com a destinatari |
| «Aquest examen només es pot fer des de l'aula…» | No ets a l'aula de l'examen. Canvia d'ordinador o de lloc |
| «L'examen no està actiu» o «s'ha tancat» | El professor l'ha desactivat o tancat. Avisa'l |
| «S'ha acabat el temps de l'examen» | El temps ha acabat; no es poden desar més respostes |
| La web no carrega o no puc entrar | Mira que escrius l'adreça exacta (amb el port). Si segueix així, avisa el professor |
| He oblidat la contrasenya | Demana-la a l'administrador: te'n posa una de nova |
| Un codi diu «temps esgotat» | El teu programa triga massa o no acaba (un bucle infinit, per exemple) |
| El desament diu «error» | Revisa la connexió. Quan torni, toca la resposta perquè es torni a desar, i espera el «desat» abans d'entregar. Si dura, avisa el professor |

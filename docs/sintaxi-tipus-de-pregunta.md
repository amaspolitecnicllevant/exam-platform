# Sintaxi de cada tipus de pregunta

Referència ràpida del format `.md` per crear exàmens i pràctiques. Cada tipus té un exemple complet que es pot copiar. La guia detallada (amb tots els casos particulars) és la *Guia de sintaxi per crear exàmens en Markdown*, que també es pot descarregar de la plataforma.

## 1. Estructura de l'examen

````markdown
# Títol de l'examen
durada: 60
instruccions: Text opcional per als alumnes (una sola línia).

---

### Part 1 — Teoria

## 1. [choice] [pts:2] [co:CO1]
Enunciat…
````

- El fitxer comença amb el títol (`# `), la durada en minuts (`durada:`, per defecte 90) i les instruccions (opcionals). Una línia `---` tanca la capçalera.
- `### Títol` obre una **secció**: les preguntes es barregen per a cada alumne dins de cada secció, però les seccions no es barregen entre elles. Afegeix `[apunts]` al títol si en aquella part es poden fer servir apunts.
- **La suma de tots els `[pts:X]` ha de ser exactament 10.**
- **Examen o pràctica** no s'escriu al fitxer: es tria a la previsualització, al desplegable «Tipus». Cada part té el seu percentatge a la nota de la unitat (es defineix a la programació del mòdul).

## 2. Capçalera de pregunta

```
## N. [tipus] [pts:X] [co:CO1] [dif:mitjana] [ordre:fix] [apunts] [formats:docx,xlsx]
```

| Etiqueta | Obligatòria | Què és |
|:--|:--:|:--|
| `[tipus]` | Sí | Un dels tipus d'aquest document (majúscules o minúscules; `bash-cmd` = `BASH_CMD`) |
| `[pts:X]` | Sí | Punts de la pregunta. Decimals amb **punt**: `[pts:0.5]` |
| `[co:CO1]` | No | Competència que treballa la pregunta |
| `[dif:NIVELL]` | No | Dificultat: només `baixa`, `mitjana` o `alta` |
| `[ordre:fix]` | No | Només a `choice`: les opcions no es barregen |
| `[apunts]` | No | En aquesta pregunta es poden fer servir apunts |
| `[formats:LLISTA]` | No | Només a `fitxer`: extensions admeses |

Les etiquetes opcionals van **després** de `[pts:X]`, en qualsevol ordre. No s'admeten etiquetes desconegudes.

## 3. Els blocs de correcció

Van **al final de la pregunta**. Cada bloc s'obre amb `:::nom` en una línia pròpia, es **tanca amb `:::`** en una línia pròpia, i s'ha de tancar abans d'obrir el següent.

| Bloc | Per a | Funció |
|:--|:--|:--|
| `:::model` | tots | `choice`: la lletra correcta. Altres: la resposta de referència que veu el professor |
| `:::clau` | `text`, `short`, `long` | Conceptes clau per proposar la nota |
| `:::output-contains` / `:::output-exact` / `:::output-regex` | preguntes de codi | Criteri de correcció de la sortida (se'n posa **un de sol**) |
| `:::test` | `bash-script`, `ps-script` | Script que comprova la solució de l'alumne |

## 4. Tipus de pregunta

| Tipus | L'alumne… | Correcció |
|:--|:--|:--|
| `choice` | tria una opció (a–d) | Automàtica, amb la penalització de l'examen |
| `short` · `text` · `long` | escriu una resposta | Proposta per conceptes clau, o manual |
| `bash-cmd` · `ps-cmd` | escriu una comanda | Automàtica per la sortida |
| `bash-script` · `ps-script` | escriu un script | Automàtica (`:::test` o sortida) |
| `java-prog` | escriu un programa (classe `Main`) | Automàtica per la sortida |
| `html-css` | escriu HTML/CSS amb previsualització | Manual |
| `fitxer` | puja un fitxer | Manual |

Les notes automàtiques de text i de codi són **propostes**: el professor les revisa abans de publicar les notes.

### 4.1 `choice` — Test

````markdown
## 1. [choice] [pts:1] [co:CO1] [dif:baixa]
Quin port fa servir per defecte un servidor DHCP?

- a) 53
- b) 67
- c) 80
- d) 443

:::model
b
:::
````

- Opcions **a, b, c i d**, una per línia, amb el format `- a) text`. La lletra correcta va a `:::model`.
- Cada alumne veu les opcions en un ordre diferent. Si una opció depèn de la posició («Totes les anteriors»), afegeix `[ordre:fix]`.
- Les respostes errades poden restar punts segons la penalització de l'examen; les en blanc, mai.

### 4.2 `short`, `text` i `long` — Resposta escrita

````markdown
## 2. [short] [pts:1] [co:CO2]
Què fa un servidor DHCP?

:::model
Assigna automàticament la configuració de xarxa (adreça IP, màscara, porta d'enllaç i DNS) als equips.
:::

:::clau
adreça IP, direcció IP, IP | 0.5
automàtic, automàticament, dinàmic | 0.5
:::
````

- `short` és una resposta breu, `text` una d'oberta i `long` una de llarga; la sintaxi és idèntica.
- `:::clau` és opcional. Una línia per concepte, **sinònims separats per comes** (n'hi ha prou que n'aparegui un). El **pes** és opcional, després de `|`: o tots els conceptes en tenen i sumen els punts de la pregunta, o cap.
- La cerca no distingeix majúscules ni accents i compara paraules senceres.

### 4.3 `bash-cmd` i `ps-cmd` — Comanda

````markdown
## 3. [bash-cmd] [pts:1] [co:CO3]
Escriu una comanda que mostri els números de l'1 al 5, un per línia.

:::model
seq 1 5
:::

:::output-contains
1
3
5
:::
````

Criteris de sortida (només **un** per pregunta):

- `:::output-contains`: cada línia del bloc ha d'aparèixer a la sortida. Amb diverses línies, la nota és proporcional.
- `:::output-exact`: la sortida ha de ser exactament aquest text.
- `:::output-regex`: la sortida ha de complir l'expressió regular (sintaxi Java, multilínia).

El programa també ha d'**acabar bé** (exit code 0). `ps-cmd` és idèntic, amb PowerShell.

### 4.4 `bash-script` i `ps-script` — Script amb prova

````markdown
## 4. [bash-script] [pts:2] [co:CO3]
Escriu un script que rebi un nom com a paràmetre i mostri `Hola, <nom>!`.

:::model
#!/bin/bash
echo "Hola, $1!"
:::

:::test
#!/bin/bash
[ "$(bash "$SCRIPT_FILE" Anna 2>/dev/null)" = "Hola, Anna!" ] && exit 0 || exit 1
:::
````

- `:::test` retorna **exit code 0** si la solució és correcta. `$SCRIPT_FILE` és la ruta de l'script de l'alumne: el test l'ha d'executar ell mateix.
- No es pot combinar `:::test` amb un `:::output-*`. Sense `:::test`, es poden fer servir els criteris de sortida de §4.3.
- Els scripts s'executen sense xarxa i amb el sistema de fitxers de només lectura, excepte `/tmp`.

### 4.5 `java-prog` — Programa Java

````markdown
## 5. [java-prog] [pts:2] [co:CO4]
Escriu un programa que mostri la suma dels números de l'1 al 10.

:::model
public class Main {
    public static void main(String[] args) {
        int suma = 0;
        for (int i = 1; i <= 10; i++) suma += i;
        System.out.println(suma);
    }
}
:::

:::output-exact
55
:::
````

La classe s'ha de dir `Main` i tenir `public static void main(String[] args)`. Es compila, s'executa i la sortida es comprova amb `:::output-*`.

### 4.6 `html-css` — HTML i CSS

````markdown
## 6. [html-css] [pts:1.5] [co:CO4]
Crea una pàgina amb un títol `h1` que digui «Benvinguts» en color vermell.

:::model
<h1 style="color: red;">Benvinguts</h1>
:::
````

L'alumne veu una previsualització del que escriu. No s'executa i no porta criteris de sortida: la corregeix el professor.

### 4.7 `fitxer` — Lliurament d'un fitxer

````markdown
## 7. [fitxer] [pts:3] [formats:docx,pkt]
Dissenya la xarxa de l'enunciat i entrega la simulació de Packet Tracer (`.pkt`) i un document amb la taula d'adreçament (`.docx`).

:::model
Ha de tenir 3 subxarxes i la taula d'adreçament completa.
:::
````

- Es corregeix a mà. El `:::model` és només una nota per al professor.
- `[formats:…]` limita les extensions. Admesos: `docx`, `xlsx`, `pptx`, `odt`, `ods`, `odp`, `pdf`, `pkt`, `pka`, `pkz`, `zip`, `png`, `jpg`, `jpeg` i `txt`. Un fitxer per pregunta, de 10 MB com a màxim.

## 5. Format dins de l'enunciat

L'enunciat admet Markdown: `**negreta**`, `` `codi` ``, llistes, blocs de codi i taules.

````markdown
## 8. [short] [pts:1]
Segons aquesta taula, quina valoració correspon a un índex de 47?

| Rang   | Valoració  |
|:------:|------------|
| 0–40   | Crítica    |
| 41–70  | Millorable |
| 71–100 | Bona       |
````

> No facis servir `###` a l'enunciat (es llegeix com una secció): usa negreta.
> Les imatges no s'escriuen al fitxer: s'afegeixen des de la previsualització, amb «Afegir imatge».

## 6. Errors freqüents

- Encadenar blocs sense tancar-los (`:::model` … `:::output-contains` … `:::`): tanca cada bloc amb `:::`.
- Que la suma dels punts no sigui 10.
- Escriure `[pts:0,5]` en lloc de `[pts:0.5]`.
- Opcions de `choice` amb lletres que no siguin a–d.
- Posar més d'un criteri `:::output-*` a la mateixa pregunta, o `:::test` junt amb un `:::output-*`.
- Fer servir l'etiqueta antiga `[ra:…]`: ara és `[co:…]`.

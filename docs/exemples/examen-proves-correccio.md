# Examen de proves — correcció assistida
durada: 30
instruccions: Examen de prova per comprovar la correcció automàtica i les propostes de nota. No compta per a cap avaluació.

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

## 2. [choice] [pts:1] [ra:RA1] [dif:mitjana]
Quina capa del model OSI s'encarrega de l'encaminament entre xarxes?

- a) Enllaç de dades
- b) Transport
- c) Xarxa
- d) Aplicació

:::model
c
:::

## 3. [short] [pts:1.5] [ra:RA2] [dif:baixa]
Què fa un servidor DHCP?

:::model
Assigna automàticament la configuració de xarxa (adreça IP, màscara, porta d'enllaç i DNS) als equips que s'hi connecten.
:::

:::clau
adreça IP, direcció IP, IP | 0.75
automàtic, automàticament, dinàmic, dinàmicament | 0.75
:::

## 4. [short] [pts:1] [ra:RA2] [dif:mitjana]
Quina diferència principal hi ha entre TCP i UDP?

:::model
TCP és orientat a connexió i fiable (garanteix l'entrega i l'ordre); UDP no té connexió ni garanties, però és més ràpid.
:::

:::clau
connexió, orientat a connexió
fiable, fiabilitat, garanteix
:::

## 5. [long] [pts:1] [ra:RA2] [dif:alta]
Explica breument com funciona la resolució d'un nom de domini (DNS) des que l'usuari escriu una adreça al navegador.

:::model
El navegador consulta la memòria cau local; si no hi és, pregunta al servidor DNS configurat, que resol recursivament preguntant als servidors arrel, als del domini de primer nivell i finalment al servidor autoritatiu del domini, i retorna l'adreça IP.
:::

### Part 2 — Pràctica

## 6. [bash-cmd] [pts:1] [ra:RA3] [dif:baixa]
Escriu una comanda que mostri els números de l'1 al 5, un per línia.

:::model
seq 1 5
:::

:::output-contains
1
3
5
:::

## 7. [bash-cmd] [pts:1] [ra:RA3] [dif:mitjana]
Escriu una comanda que mostri quants caràcters té la paraula `politecnic` (només el número).

:::model
echo -n politecnic | wc -c
:::

:::output-exact
10
:::

## 8. [bash-cmd] [pts:0.5] [ra:RA3] [dif:baixa]
Escriu una comanda que mostri la data d'avui en format `AAAA-MM-DD`.

:::model
date +%F
:::

:::output-regex
^\d{4}-\d{2}-\d{2}$
:::

## 9. [bash-script] [pts:1] [ra:RA3] [dif:mitjana]
Escriu un script que rebi un nom com a primer paràmetre i mostri exactament `Hola, <nom>!`. Si no rep cap paràmetre, ha de mostrar `Falta el nom` i acabar amb codi d'error 1.

:::model
#!/bin/bash
if [ -z "$1" ]; then
  echo "Falta el nom"
  exit 1
fi
echo "Hola, $1!"
:::

:::test
#!/bin/bash
[ "$(bash "$SCRIPT_FILE" Anna 2>/dev/null)" = "Hola, Anna!" ] || exit 1
bash "$SCRIPT_FILE" >/dev/null 2>&1 && exit 1
exit 0
:::

## 10. [java-prog] [pts:1] [ra:RA4] [dif:mitjana]
Escriu un programa Java (classe `Main`) que mostri la suma dels números de l'1 al 100.

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

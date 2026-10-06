import { Link } from 'react-router-dom'
import Layout from '../../components/Layout'

const EXEMPLE_TEXT = `# Examen de Xarxes — 1r ASIX
durada: 60
instruccions: Llegeix bé cada pregunta. La penalització per resposta incorrecta és d'1/3.

---

### Part 1: Model OSI

## 1 [TEXT] [pts:2]
Explica la diferència entre les capes de transport i de xarxa del model OSI.

:::model
Capa de xarxa (L3): encaminament entre xarxes, adreçament IP.
Capa de transport (L4): comunicació extrem a extrem, segmentació, TCP/UDP.
:::

## 2 [CHOICE] [pts:2]
Quina és la unitat de dades de la capa d'enllaç (L2)?

- a) Segment
- b) Paquet
- c) Trama
- d) Bit

:::model
c
:::

### Part 2: Administració Linux

## 3 [BASH_CMD] [pts:2]
Escriu la comanda per llistar totes les interfícies de xarxa amb les seves adreces IP.

:::model
ip addr show
:::

:::output-contains
inet
:::

## 4 [BASH_SCRIPT] [pts:2]
Escriu un script bash que mostri el nombre de línies del fitxer /etc/passwd.

:::test
#!/bin/bash
set -e
result=$(bash "$SCRIPT_FILE")
expected=$(wc -l < /etc/passwd)
[ "$result" -eq "$expected" ]
:::

## 5 [SHORT] [pts:2]
Quin protocol usa el port 443?

:::model
HTTPS (HTTP sobre TLS).
:::

:::clau
HTTPS
:::
`

const EXEMPLE_MINIMAL = `# Títol de l'examen
durada: 90

---

## 1 [CHOICE] [pts:5]
Pregunta d'opció múltiple...

- a) Primera opció
- b) Segona opció
- c) Tercera opció
- d) Quarta opció

:::model
b
:::

## 2 [TEXT] [pts:5]
Pregunta de resposta oberta...
`

type Row = { cap: string; desc: string }

function Table({ rows }: { rows: Row[] }) {
  return (
    <table className="w-full text-sm border-collapse">
      <thead>
        <tr className="bg-gray-100">
          <th className="text-left px-3 py-2 border border-gray-200 font-mono font-semibold w-40">Tipus</th>
          <th className="text-left px-3 py-2 border border-gray-200 font-semibold">Descripció</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.cap} className="hover:bg-gray-50">
            <td className="px-3 py-2 border border-gray-200 font-mono text-brand-700">{r.cap}</td>
            <td className="px-3 py-2 border border-gray-200 text-gray-700">{r.desc}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function CodeBlock({ code }: { code: string }) {
  return (
    <pre className="bg-gray-900 text-gray-100 rounded-xl p-4 text-xs overflow-x-auto leading-relaxed">
      <code>{code}</code>
    </pre>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-gray-800 border-b border-gray-200 pb-2">{title}</h2>
      {children}
    </section>
  )
}

export default function ExamSintaxiPage() {
  return (
    <Layout>
      <div className="max-w-3xl space-y-8">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-brand-700">Format del fitxer .md d'examen</h1>
            <p className="text-sm text-gray-500 mt-1">
              Resum per crear exàmens importables. La guia descarregable té tots els detalls, exemples
              validats i instruccions per generar exàmens amb una IA.
            </p>
          </div>
          <div className="flex flex-wrap gap-2 justify-end">
            <a href="/guia-sintaxi-examens.md" download="guia-sintaxi-examens.md"
              title="Guia completa en Markdown, també per donar-la a una IA perquè generi exàmens"
              className="text-sm bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700">
              Descarregar guia (.md)
            </a>
            <Link to="/professor/exams/new"
              className="text-sm border border-brand-600 text-brand-600 px-4 py-2 rounded-lg hover:bg-brand-50">
              Importar examen
            </Link>
          </div>
        </div>

        <Section title="Estructura general">
          <p className="text-sm text-gray-600">
            El fitxer té dues parts separades per <code className="bg-gray-100 px-1 rounded">---</code>:
            la <strong>capçalera</strong> i el <strong>cos de preguntes</strong>.
          </p>
          <CodeBlock code={EXEMPLE_MINIMAL} />
        </Section>

        <Section title="Capçalera">
          <Table rows={[
            { cap: '# Títol', desc: 'Títol de l\'examen. Obligatori.' },
            { cap: 'durada: N', desc: 'Durada en minuts (per defecte: 90).' },
            { cap: 'instruccions: text', desc: 'Text d\'instruccions per als alumnes. Opcional.' },
          ]} />
        </Section>

        <Section title="Capçalera de pregunta">
          <p className="text-sm text-gray-600">
            Cada pregunta comença amb una línia <code className="bg-gray-100 px-1 rounded">##</code>:
          </p>
          <CodeBlock code={`## 3 [CHOICE] [pts:2.5] [ra:RA1] [dif:mitjana] [ordre:fix]`} />
          <Table rows={[
            { cap: 'N', desc: 'Número d\'ordre de la pregunta (enter positiu, únic).' },
            { cap: '[TIPUS]', desc: 'Tipus de pregunta (vegeu la taula de tipus).' },
            { cap: '[pts:X]', desc: 'Punts de la pregunta (decimal amb punt). La suma total ha de ser exactament 10.' },
            { cap: '[ra:RA1]', desc: 'Opcional. Resultat d\'aprenentatge; permet veure la nota per RA.' },
            { cap: '[dif:mitjana]', desc: 'Opcional. Dificultat: baixa, mitjana o alta.' },
            { cap: '[apunts]', desc: 'Opcional. En aquesta pregunta es poden fer servir apunts en paper. També es pot posar al títol d\'una secció (### Pràctica [apunts]) per marcar-ne totes les preguntes.' },
            { cap: '[formats:docx,pkt]', desc: 'Opcional, només [fitxer]. Extensions que l\'alumne pot pujar (docx, xlsx, pptx, odt, ods, odp, pdf, pkt, pka, pkz, zip, png, jpg, txt). Sense l\'etiqueta, s\'admeten tots.' },
            { cap: '[ordre:fix]', desc: 'Opcional, només CHOICE. Les opcions no es barregen (per defecte cada alumne les veu en un ordre diferent). Útil per a «Totes les anteriors».' },
          ]} />
        </Section>

        <Section title="Tipus de pregunta">
          <Table rows={[
            { cap: 'TEXT',        desc: 'Resposta de text oberta. Amb :::clau, la plataforma proposa la nota; si no, correcció manual.' },
            { cap: 'SHORT',       desc: 'Resposta curta. Amb :::clau, la plataforma proposa la nota; si no, correcció manual.' },
            { cap: 'LONG',        desc: 'Resposta extensa. Amb :::clau, la plataforma proposa la nota; si no, correcció manual.' },
            { cap: 'CHOICE',      desc: 'Selecció múltiple (a, b, c, d). Correcció automàtica.' },
            { cap: 'BASH_CMD',    desc: 'Alumne escriu una comanda bash. Execució automàtica al contenidor.' },
            { cap: 'BASH_SCRIPT', desc: 'Alumne escriu un script bash complet. Execució i comprovació automàtica.' },
            { cap: 'PS_CMD',      desc: 'Alumne escriu una comanda PowerShell. Execució automàtica.' },
            { cap: 'PS_SCRIPT',   desc: 'Alumne escriu un script PowerShell. Execució i comprovació automàtica.' },
            { cap: 'JAVA_PROG',   desc: 'Alumne escriu un programa Java. La classe principal ha de dir-se Main. Compilació i execució automàtica.' },
            { cap: 'HTML_CSS',    desc: 'Alumne escriu HTML/CSS. Previsualització en directe al navegador. Correcció manual.' },
            { cap: 'FITXER',      desc: 'L\'alumne puja un fitxer (Word, Excel, Packet Tracer…), de fins a 10 MB. Es pot escriure [fitxer]. Correcció manual: el professor el descarrega i el qualifica.' },
          ]} />
        </Section>

        <Section title="Seccions (opcional)">
          <p className="text-sm text-gray-600">
            Pots agrupar preguntes amb títols de secció usant <code className="bg-gray-100 px-1 rounded">###</code>.
            Les seccions no puntuen i només serveixen per organitzar visualment l'examen.
          </p>
          <CodeBlock code={`### Part 2: Administració Linux\n\n## 3 [TEXT] [pts:2]\n...`} />
        </Section>

        <Section title="Blocs especials">
          <p className="text-sm text-gray-600">
            Després de l'enunciat d'una pregunta pots afegir blocs <code className="bg-gray-100 px-1 rounded">:::nom</code> per
            definir la correcció o validació automàtica:
          </p>
          <Table rows={[
            { cap: ':::model',          desc: 'Per a CHOICE: la lletra correcta (a/b/c/d). Per a TEXT/SHORT/LONG: resposta model per al professor.' },
            { cap: ':::clau',           desc: 'Per a TEXT/SHORT/LONG (opcional): conceptes clau, un per línia, sinònims separats per comes i pes opcional amb "| punts". Serveix per proposar la nota i els motius de penalització.' },
            { cap: ':::output-contains', desc: 'Per a BASH_CMD/PS_CMD: la sortida ha de contenir aquest text (sense distinció maj/min). L\'execució ha d\'acabar amb exit 0.' },
            { cap: ':::output-exact',    desc: 'Per a BASH_CMD/PS_CMD: la sortida ha de ser exactament aquest text. L\'execució ha d\'acabar amb exit 0.' },
            { cap: ':::output-regex',    desc: 'Per a BASH_CMD/PS_CMD: la sortida ha de complir aquesta expressió regular. L\'execució ha d\'acabar amb exit 0.' },
            { cap: ':::test',            desc: 'Per a BASH_SCRIPT/PS_SCRIPT: script de comprovació. Si acaba amb exit 0, la resposta és correcta.' },
          ]} />
          <p className="text-sm text-gray-500">
            Les preguntes CHOICE <strong>requereixen</strong> el bloc <code className="bg-gray-100 px-1 rounded">:::model</code>.
            La resta de blocs són opcionals.
          </p>
        </Section>

        <Section title="Opcions múltiples (CHOICE)">
          <p className="text-sm text-gray-600">
            Les opcions s'escriuen com a ítems de llista amb la lletra seguida de parèntesi.
            Accepta a, b, c i d.
          </p>
          <CodeBlock code={`## 2 [CHOICE] [pts:2]\nQuina capa del model OSI s'encarrega de l'encaminament?\n\n- a) Capa de transport\n- b) Capa de xarxa\n- c) Capa d'enllaç\n- d) Capa física\n\n:::model\nb\n:::`} />
        </Section>

        <Section title="Exemple complet">
          <CodeBlock code={EXEMPLE_TEXT} />
        </Section>

        <Section title="Regles importants">
          <ul className="text-sm text-gray-700 space-y-1.5 list-disc list-inside">
            <li>La <strong>suma de punts</strong> de totes les preguntes ha de ser exactament <strong>10</strong>.</li>
            <li>El separador <code className="bg-gray-100 px-1 rounded">---</code> entre capçalera i preguntes és obligatori.</li>
            <li>El títol (<code className="bg-gray-100 px-1 rounded"># Títol</code>) és obligatori.</li>
            <li>Els números d'ordre de pregunta han de ser enters positius (no cal que siguin consecutius).</li>
            <li>Les preguntes executables (BASH_*, PS_*, JAVA_PROG) s'executen en contenidors <strong>aïllats</strong> entre si: cada alumne té el seu directori d'execució privat.</li>
            <li>Per a JAVA_PROG, la classe principal <strong>ha de dir-se exactament <code className="bg-gray-100 px-1 rounded">Main</code></strong>.</li>
            <li>Si la pregunta té fitxers de dades adjunts (puja'ls des de la previsualització de l'examen), l'alumne els pot descarregar i estan disponibles al contenidor a <code className="bg-gray-100 px-1 rounded">/data/files/</code>.</li>
            <li>Les preguntes HTML_CSS mostren una previsualització en temps real al navegador de l'alumne. No s'executen al servidor.</li>
            <li>La codificació del fitxer ha de ser <strong>UTF-8</strong>.</li>
          </ul>
        </Section>

        <div className="flex justify-center pt-2">
          <Link to="/professor/exams/new"
            className="bg-brand-600 text-white px-6 py-2.5 rounded-lg text-sm hover:bg-brand-700">
            Importar examen
          </Link>
        </div>
      </div>
    </Layout>
  )
}

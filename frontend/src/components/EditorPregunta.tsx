import { useRef, useState } from 'react'
import Md from './Md'
import { afegeixPregunta, actualitzaPregunta, pujaImatge, type PreguntaEdicio } from '../api/editor'
import { deleteQuestionFile } from '../api/questionFiles'
import type { Question, QuestionFile, QuestionType } from '../types'
import { esImatge, extensioPermesa, idsReferenciats, insereix, markdownImatge, substitueix, treuReferencies } from '../utils/imatges'

const TIPUS: { valor: QuestionType; text: string }[] = [
  { valor: 'SHORT', text: 'Resposta curta' },
  { valor: 'LONG', text: 'Resposta llarga' },
  { valor: 'TEXT', text: 'Text' },
  { valor: 'CHOICE', text: 'Test (opcions)' },
  { valor: 'BASH_CMD', text: 'Comanda Bash' },
  { valor: 'PS_CMD', text: 'Comanda PowerShell' },
  { valor: 'BASH_SCRIPT', text: 'Script Bash' },
  { valor: 'PS_SCRIPT', text: 'Script PowerShell' },
  { valor: 'JAVA_PROG', text: 'Programa Java' },
  { valor: 'HTML_CSS', text: 'HTML/CSS' },
  { valor: 'FILE_UPLOAD', text: 'Lliurament de fitxer' },
  { valor: 'SECTION', text: 'Secció (títol)' },
]
const TEXTUALS: QuestionType[] = ['TEXT', 'SHORT', 'LONG']
const EXECUTABLES: QuestionType[] = ['BASH_CMD', 'PS_CMD', 'BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
const SCRIPTS: QuestionType[] = ['BASH_SCRIPT', 'PS_SCRIPT']
const MAX_OPCIONS = 4
const MAX_IMATGE_MB = 5
const LLETRES = 'abcd'

type Criteri = 'cap' | 'contains' | 'exact' | 'regex' | 'test'

interface Pendent { marcador: string; file: File; url: string }

interface Props {
  examId: string
  /** Pregunta que s'edita; sense valor, se'n crea una de nova. */
  pregunta?: Question
  /** Només en crear: posició on s'insereix (1 = primera). */
  posicio?: number
  /** Fitxers actuals de la pregunta (per mostrar-ne les imatges). */
  fitxers?: QuestionFile[]
  onClose: () => void
  /** Cridada quan s'ha desat: el pare recarrega l'examen. */
  onDesat: () => void
}

const campCls = 'w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-brand-400'
const etiquetaCls = 'block text-xs font-medium text-gray-600 mb-1'

function criteriInicial(q?: Question): Criteri {
  if (!q) return 'cap'
  if (q.testScript) return 'test'
  if (q.outputExact) return 'exact'
  if (q.outputContains) return 'contains'
  if (q.outputRegex) return 'regex'
  return 'cap'
}

export default function EditorPregunta({ examId, pregunta, posicio, fitxers = [], onClose, onDesat }: Props) {
  const q = pregunta
  const [tipus, setTipus] = useState<QuestionType>(q?.tipus ?? 'SHORT')
  const [enunciat, setEnunciat] = useState(q?.enunciat ?? '')
  const [punts, setPunts] = useState(q ? String(q.punts) : '1')
  const [opcions, setOpcions] = useState<string[]>(
    q?.choices?.map(c => c.replace(/^[a-zA-Z]\)\s*/, '')) ?? ['', ''])
  const [correcta, setCorrecta] = useState<number>(() => {
    const lletres = q?.choices?.map(c => c.charAt(0).toLowerCase()) ?? []
    const i = lletres.indexOf((q?.correctChoice ?? '').toLowerCase())
    return i >= 0 ? i : 0
  })
  const [barrejar, setBarrejar] = useState(q?.barrejarOpcions ?? true)
  const [model, setModel] = useState(q?.tipus === 'CHOICE' ? '' : q?.modelResposta ?? '')
  const [criteri, setCriteri] = useState<Criteri>(criteriInicial(q))
  const [criteriText, setCriteriText] = useState(q?.testScript ?? q?.outputExact ?? q?.outputContains ?? q?.outputRegex ?? '')
  const [claus, setClaus] = useState(q?.claus ?? '')
  const [ra, setRa] = useState(q?.ra ?? '')
  const [dificultat, setDificultat] = useState<string>(q?.dificultat ?? '')
  const [apunts, setApunts] = useState(q?.ambApunts ?? false)
  const [formats, setFormats] = useState((q?.formatsPermesos ?? []).join(', '))
  const [error, setError] = useState('')
  const [enviant, setEnviant] = useState(false)
  const [previsualitza, setPrevisualitza] = useState(false)

  // Imatges: les que ja són al servidor, les que es pugen en desar (pregunta nova) i les que s'esborraran en desar
  const [imatges, setImatges] = useState<QuestionFile[]>(fitxers.filter(esImatge))
  const [pendents, setPendents] = useState<Pendent[]>([])
  const [perEsborrar, setPerEsborrar] = useState<string[]>([])
  const [pujant, setPujant] = useState(false)
  const textRef = useRef<HTMLTextAreaElement>(null)
  const fitxerRef = useRef<HTMLInputElement>(null)
  const comptador = useRef(0)

  const esSeccio = tipus === 'SECTION'
  const esTest = tipus === 'CHOICE'
  const esText = TEXTUALS.includes(tipus)
  const esExec = EXECUTABLES.includes(tipus)
  const esScript = SCRIPTS.includes(tipus)
  const esFitxer = tipus === 'FILE_UPLOAD'
  const locals = Object.fromEntries(pendents.map(p => [p.marcador, p.url]))

  const afegeixImatgeAlText = (id: string, nom: string) => {
    const t = textRef.current
    const r = insereix(enunciat, markdownImatge(id, nom.replace(/\.[^.]+$/, '')), t?.selectionStart ?? null, t?.selectionEnd ?? null)
    setEnunciat(r.text)
    requestAnimationFrame(() => { t?.focus(); t?.setSelectionRange(r.cursor, r.cursor) })
  }

  const triaImatge = async (file: File) => {
    setError('')
    if (!extensioPermesa(file.name)) return setError('Format no admès. Usa PNG, JPG, GIF o WebP')
    if (file.size > MAX_IMATGE_MB * 1024 * 1024) return setError(`La imatge pesa massa (màxim ${MAX_IMATGE_MB} MB)`)
    if (!q) {
      // Pregunta encara no creada: es puja en desar
      const marcador = `nou-${++comptador.current}`
      setPendents(p => [...p, { marcador, file, url: URL.createObjectURL(file) }])
      afegeixImatgeAlText(marcador, file.name)
      return
    }
    setPujant(true)
    try {
      const f = await pujaImatge(q.id, file)
      setImatges(i => [...i, f])
      afegeixImatgeAlText(f.id, file.name)
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'ha pogut pujar la imatge')
    } finally {
      setPujant(false)
    }
  }

  const treuImatge = (f: QuestionFile) => {
    setEnunciat(t => treuReferencies(t, f.id))
    setImatges(i => i.filter(x => x.id !== f.id))
    setPerEsborrar(p => [...p, f.id])
  }

  const treuPendent = (p: Pendent) => {
    setEnunciat(t => treuReferencies(t, p.marcador))
    setPendents(l => l.filter(x => x.marcador !== p.marcador))
    URL.revokeObjectURL(p.url)
  }

  const construeix = (text: string): PreguntaEdicio => {
    const base: PreguntaEdicio = { tipus, enunciat: text }
    if (esSeccio) return base
    base.punts = Number(punts.replace(',', '.'))
    base.ra = ra.trim()
    base.dificultat = dificultat
    base.ambApunts = apunts
    if (esTest) {
      base.opcions = opcions.map(o => o.trim())
      base.correctChoice = LLETRES.charAt(correcta)
      base.barrejarOpcions = barrejar
      return base
    }
    base.modelResposta = model
    if (esText) base.claus = claus
    if (esExec && criteri === 'contains') base.outputContains = criteriText
    if (esExec && criteri === 'exact') base.outputExact = criteriText
    if (esExec && criteri === 'regex') base.outputRegex = criteriText
    if (esScript && criteri === 'test') base.testScript = criteriText
    if (esFitxer) base.formatsPermesos = formats.split(/[,\s]+/).map(f => f.replace(/^\./, '').toLowerCase()).filter(Boolean)
    return base
  }

  const desa = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    if (!esSeccio && !(Number(punts.replace(',', '.')) > 0)) return setError('Els punts han de ser un nombre positiu')
    setEnviant(true)
    try {
      if (q) {
        await actualitzaPregunta(examId, q.id, construeix(enunciat))
        for (const id of perEsborrar) {
          try { await deleteQuestionFile(q.id, id) } catch { /* ja no hi és: res a fer */ }
        }
      } else {
        const creada = await afegeixPregunta(examId, { ...construeix(enunciat), posicio })
        const usats = idsReferenciats(enunciat)
        const ids: Record<string, string> = {}
        try {
          for (const p of pendents.filter(x => usats.has(x.marcador))) ids[p.marcador] = (await pujaImatge(creada.id, p.file)).id
          if (Object.keys(ids).length > 0) await actualitzaPregunta(examId, creada.id, construeix(substitueix(enunciat, ids)))
        } catch (err: any) {
          onDesat()
          return setError('La pregunta s\'ha creat, però no s\'han pogut pujar les imatges: '
            + (err?.response?.data?.error ?? 'error desconegut') + '. Edita-la per afegir-les.')
        }
      }
      pendents.forEach(p => URL.revokeObjectURL(p.url))
      onDesat()
      onClose()
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut desar la pregunta')
    } finally {
      setEnviant(false)
    }
  }

  const canviaOpcio = (i: number, v: string) => setOpcions(o => o.map((x, j) => (j === i ? v : x)))
  const treuOpcio = (i: number) => {
    setOpcions(o => o.filter((_, j) => j !== i))
    setCorrecta(c => (c === i ? 0 : c > i ? c - 1 : c))
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-start justify-center z-50 p-4 overflow-y-auto">
      <form onSubmit={desa} className="bg-white rounded-xl shadow-xl w-full max-w-2xl p-6 space-y-4 my-4 text-gray-900">
        <h2 className="text-lg font-semibold">{q ? `Editar la pregunta ${q.ordre}` : 'Afegir una pregunta'}</h2>

        <div className="grid grid-cols-3 gap-3">
          <label className="col-span-2">
            <span className={etiquetaCls}>Tipus</span>
            <select value={tipus} onChange={e => setTipus(e.target.value as QuestionType)} className={campCls}>
              {TIPUS.map(t => <option key={t.valor} value={t.valor}>{t.text}</option>)}
            </select>
          </label>
          {!esSeccio && (
            <label>
              <span className={etiquetaCls}>Punts</span>
              <input value={punts} onChange={e => setPunts(e.target.value)} inputMode="decimal" className={campCls} />
            </label>
          )}
        </div>

        <div>
          <div className="flex items-center justify-between mb-1">
            <span className={etiquetaCls + ' mb-0'}>{esSeccio ? 'Títol de la secció' : 'Enunciat (admet Markdown)'}</span>
            {!esSeccio && (
              <div className="flex items-center gap-2 text-xs">
                <button type="button" onClick={() => setPrevisualitza(p => !p)} className="text-brand-600 hover:underline">
                  {previsualitza ? 'Editar' : 'Previsualitzar'}
                </button>
                <input ref={fitxerRef} type="file" accept=".png,.jpg,.jpeg,.gif,.webp,image/png,image/jpeg,image/gif,image/webp"
                  className="hidden"
                  onChange={async e => {
                    const f = e.target.files?.[0]
                    if (f) await triaImatge(f)
                    e.target.value = ''
                  }} />
                <button type="button" disabled={pujant} onClick={() => fitxerRef.current?.click()}
                  className="bg-brand-600 text-white px-2 py-1 rounded hover:bg-brand-700 disabled:opacity-50">
                  {pujant ? 'Pujant…' : '🖼 Afegir imatge'}
                </button>
              </div>
            )}
          </div>
          {previsualitza && !esSeccio
            ? <div className="border rounded p-3 min-h-[8rem] bg-gray-50"><Md imatgesLocals={locals}>{enunciat}</Md></div>
            : <textarea ref={textRef} value={enunciat} onChange={e => setEnunciat(e.target.value)}
                rows={esSeccio ? 1 : 7} className={campCls + ' font-mono'} />}
          {(imatges.length > 0 || pendents.length > 0) && !esSeccio && (
            <div className="flex flex-wrap gap-2 mt-2">
              {imatges.map(f => (
                <span key={f.id} className="flex items-center gap-1 bg-gray-50 border rounded px-2 py-0.5 text-xs">
                  🖼 {f.filename}
                  {!idsReferenciats(enunciat).has(f.id) && (
                    <button type="button" onClick={() => afegeixImatgeAlText(f.id, f.filename)} className="text-brand-600 hover:underline">inserir</button>
                  )}
                  <button type="button" onClick={() => treuImatge(f)} className="text-red-500 hover:text-red-700" title="Treure i esborrar la imatge en desar">×</button>
                </span>
              ))}
              {pendents.map(p => (
                <span key={p.marcador} className="flex items-center gap-1 bg-amber-50 border border-amber-200 rounded px-2 py-0.5 text-xs">
                  🖼 {p.file.name} <em className="text-amber-700">(es puja en desar)</em>
                  <button type="button" onClick={() => treuPendent(p)} className="text-red-500 hover:text-red-700">×</button>
                </span>
              ))}
            </div>
          )}
        </div>

        {esTest && (
          <div className="space-y-2">
            <span className={etiquetaCls}>Opcions (marca la correcta)</span>
            {opcions.map((o, i) => (
              <div key={i} className="flex items-center gap-2">
                <input type="radio" name="correcta" checked={correcta === i} onChange={() => setCorrecta(i)}
                  className="accent-brand-600" title="Resposta correcta" />
                <span className="text-sm font-mono text-gray-500 w-5">{LLETRES.charAt(i)})</span>
                <input value={o} onChange={e => canviaOpcio(i, e.target.value)} className={campCls} />
                {opcions.length > 2 && (
                  <button type="button" onClick={() => treuOpcio(i)} className="text-red-500 hover:text-red-700 px-1">×</button>
                )}
              </div>
            ))}
            {opcions.length < MAX_OPCIONS && (
              <button type="button" onClick={() => setOpcions(o => [...o, ''])} className="text-xs text-brand-600 hover:underline">
                + Afegir opció
              </button>
            )}
            <label className="flex items-center gap-2 text-xs text-gray-600">
              <input type="checkbox" checked={barrejar} onChange={e => setBarrejar(e.target.checked)} className="accent-brand-600" />
              Barrejar l'ordre de les opcions per a cada alumne
            </label>
          </div>
        )}

        {!esSeccio && !esTest && (
          <label className="block">
            <span className={etiquetaCls}>
              {esFitxer ? 'Nota per al professor (opcional)' : 'Resposta model (només la veu el professor)'}
            </span>
            <textarea value={model} onChange={e => setModel(e.target.value)} rows={4} className={campCls + ' font-mono'} />
          </label>
        )}

        {esText && (
          <label className="block">
            <span className={etiquetaCls}>Conceptes clau per proposar la nota (opcional, vegeu la guia de sintaxi)</span>
            <textarea value={claus} onChange={e => setClaus(e.target.value)} rows={3} className={campCls + ' font-mono'} />
          </label>
        )}

        {esExec && (
          <div className="space-y-2">
            <label className="block">
              <span className={etiquetaCls}>Com es corregeix</span>
              <select value={criteri} onChange={e => setCriteri(e.target.value as Criteri)} className={campCls}>
                <option value="cap">Sense criteri automàtic</option>
                <option value="contains">La sortida conté…</option>
                <option value="exact">La sortida és exactament…</option>
                <option value="regex">La sortida compleix l'expressió regular…</option>
                {esScript && <option value="test">Script de test</option>}
              </select>
            </label>
            {criteri !== 'cap' && (
              <textarea value={criteriText} onChange={e => setCriteriText(e.target.value)} rows={4}
                className={campCls + ' font-mono'} />
            )}
          </div>
        )}

        {esFitxer && (
          <label className="block">
            <span className={etiquetaCls}>Formats admesos, separats per comes (buit = tots els permesos)</span>
            <input value={formats} onChange={e => setFormats(e.target.value)} placeholder="docx, xlsx, pkt" className={campCls} />
          </label>
        )}

        {!esSeccio && (
          <div className="grid grid-cols-3 gap-3 items-end">
            <label>
              <span className={etiquetaCls}>RA</span>
              <input value={ra} onChange={e => setRa(e.target.value)} placeholder="RA1" className={campCls} />
            </label>
            <label>
              <span className={etiquetaCls}>Dificultat</span>
              <select value={dificultat} onChange={e => setDificultat(e.target.value)} className={campCls}>
                <option value="">—</option>
                <option value="baixa">baixa</option>
                <option value="mitjana">mitjana</option>
                <option value="alta">alta</option>
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm pb-2">
              <input type="checkbox" checked={apunts} onChange={e => setApunts(e.target.checked)} className="accent-brand-600" />
              Amb apunts
            </label>
          </div>
        )}

        {error && <p role="alert" className="text-red-600 text-sm whitespace-pre-line">{error}</p>}

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} className="border border-gray-300 rounded px-4 py-2 text-sm">Cancel·la</button>
          <button type="submit" disabled={enviant} className="bg-brand-600 text-white rounded px-4 py-2 text-sm disabled:opacity-50">
            {enviant ? 'Desant…' : 'Desa'}
          </button>
        </div>
      </form>
    </div>
  )
}

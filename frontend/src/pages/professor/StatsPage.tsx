import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getExam, getExamStats, getRecuperacio, crearGrupRecuperacio } from '../../api/exams'
import type { Exam, ExamStats, Grup, PreguntaStats, Recuperacio } from '../../types'

const fmt = (n: number | null | undefined, dec = 2) =>
  n == null ? '—' : Number(n.toFixed(dec)).toString().replace('.', ',')

/** Rendiment per sota del qual una pregunta es marca com a difícil. */
const RENDIMENT_BAIX = 40

type Ordre = 'ordre' | 'rendiment'

export default function StatsPage() {
  const { examId } = useParams<{ examId: string }>()
  const [exam, setExam] = useState<Exam | null>(null)
  const [stats, setStats] = useState<ExamStats | null>(null)
  const [error, setError] = useState('')
  const [ordre, setOrdre] = useState<Ordre>('ordre')

  useEffect(() => {
    if (!examId) return
    Promise.all([getExam(examId), getExamStats(examId)])
      .then(([e, s]) => { setExam(e); setStats(s) })
      .catch(err => setError(err?.response?.data?.error || 'No s\'han pogut carregar les estadístiques'))
  }, [examId])

  const preguntes = useMemo(() => {
    if (!stats) return []
    const llista = [...stats.preguntes]
    if (ordre === 'rendiment') {
      llista.sort((a, b) => (a.percentRendiment ?? 101) - (b.percentRendiment ?? 101))
    }
    return llista
  }, [stats, ordre])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!stats || !exam) return <Layout><p className="text-gray-400 text-sm">Carregant…</p></Layout>

  const senseDades = stats.entregats === 0

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-brand-700">Estadístiques</h1>
            <p className="text-sm text-gray-600">{exam.title}</p>
          </div>
          <div className="flex gap-2 print:hidden">
            <Link to={`/professor/exams/${examId}/corrections`}
              className="text-sm border border-brand-600 text-brand-600 px-4 py-2 rounded-lg hover:bg-brand-50">
              Correcció
            </Link>
            <button onClick={() => window.print()}
              title="Des del diàleg d'impressió pots triar «Desa com a PDF»"
              className="text-sm bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700">
              Imprimir / desar en PDF
            </button>
          </div>
        </div>

        {stats.respostesPendents > 0 && (
          <p role="status" className="text-sm bg-amber-50 border border-amber-200 text-amber-900 rounded-lg px-4 py-2">
            ⚠ <strong>Estadístiques provisionals:</strong> hi ha {stats.respostesPendents} respostes sense nota,
            que de moment compten 0.
          </p>
        )}

        {senseDades ? (
          <p className="text-sm text-gray-500 bg-white border rounded-xl p-6">
            Encara no ho ha entregat cap alumne ({stats.sessions} sessions en total).
          </p>
        ) : (
          <>
            {/* Resum */}
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
              <Tile label="Entregats" valor={`${stats.entregats}`} detall={`de ${stats.sessions}`} />
              <Tile label="Mitjana" valor={fmt(stats.mitjana)} detall="/ 10" />
              <Tile label="Mediana" valor={fmt(stats.mediana)} detall="/ 10" />
              <Tile label="Mínima – màxima" valor={`${fmt(stats.minima)} – ${fmt(stats.maxima)}`} />
              <Tile label="Aprovats" valor={`${fmt(stats.percentAprovats, 1)} %`} detall="nota ≥ 5" />
            </div>

            {/* Histograma */}
            <section className="bg-white border rounded-xl p-5 space-y-3 break-inside-avoid">
              <h2 className="text-sm font-semibold text-gray-800">Distribució de notes</h2>
              <Histograma franges={stats.histograma} />
              <details className="text-sm print:hidden">
                <summary className="text-xs text-gray-500 cursor-pointer">Veure en taula</summary>
                <table className="mt-2 text-xs border-collapse">
                  <thead><tr><th className="text-left pr-6 py-1 text-gray-500 font-medium">Nota</th><th className="text-right py-1 text-gray-500 font-medium">Alumnes</th></tr></thead>
                  <tbody>
                    {stats.histograma.map((n, i) => (
                      <tr key={i} className="border-t"><td className="pr-6 py-1">{franja(i)}</td><td className="text-right tabular-nums">{n}</td></tr>
                    ))}
                  </tbody>
                </table>
              </details>
            </section>
          </>
        )}

        {/* Per pregunta */}
        <section className="bg-white border rounded-xl p-5 space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-sm font-semibold text-gray-800">Per pregunta</h2>
            <label className="text-xs text-gray-600 flex items-center gap-2 print:hidden">
              Ordena per
              <select value={ordre} onChange={e => setOrdre(e.target.value as Ordre)}
                className="border rounded px-2 py-1 text-xs">
                <option value="ordre">ordre de l'examen</option>
                <option value="rendiment">rendiment (les més difícils primer)</option>
              </select>
            </label>
          </div>
          <p className="text-xs text-gray-500">
            <strong>Rendiment</strong>: punts mitjans obtinguts sobre els punts de la pregunta.
            {' '}<strong>Encerts</strong>: alumnes que han triat l'opció correcta (preguntes de test).
          </p>
          <div className="overflow-x-auto">
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="text-left text-xs text-gray-500">
                  <th className="py-2 pr-3 font-medium">Pregunta</th>
                  <th className="py-2 pr-3 font-medium">Punts</th>
                  <th className="py-2 pr-3 font-medium min-w-[10rem]">Rendiment</th>
                  <th className="py-2 pr-3 font-medium text-right whitespace-nowrap">Encerts</th>
                  <th className="py-2 font-medium text-right whitespace-nowrap">En blanc</th>
                </tr>
              </thead>
              <tbody>
                {preguntes.map(p => <FilaPregunta key={p.id} p={p} entregats={stats.entregats} />)}
              </tbody>
            </table>
          </div>
        </section>

        {stats.sessions > 0 && <PanellRecuperacio examId={examId!} titol={exam.title} />}

        <p className="text-xs text-gray-400">
          Només compten els exàmens entregats. Les preguntes amb bonus compten els punts sencers per a tothom.
          El temps de resposta per pregunta no es registra.
        </p>
      </div>
    </Layout>
  )
}

function franja(i: number) {
  return i === 9 ? '9 – 10' : `${i} – ${i + 1}`
}

function Tile({ label, valor, detall }: { label: string; valor: string; detall?: string }) {
  return (
    <div className="bg-white border rounded-xl px-4 py-3">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-2xl font-bold text-gray-900 tabular-nums">{valor}</p>
      {detall && <p className="text-xs text-gray-400">{detall}</p>}
    </div>
  )
}

/** Histograma de notes: una sola sèrie (color de marca), línia d'aprovat al 5 i tooltip per barra. */
function Histograma({ franges }: { franges: number[] }) {
  const [actiu, setActiu] = useState<number | null>(null)
  const W = 600, H = 220, M = { t: 16, r: 8, b: 30, l: 32 }
  // Marques de l'eix Y: enters regulars (0, pas, 2·pas…) fins a cobrir el màxim
  const pas = Math.max(1, Math.ceil(Math.max(1, ...franges) / 4))
  const max = Math.ceil(Math.max(1, ...franges) / pas) * pas
  const bandW = (W - M.l - M.r) / franges.length
  const barW = bandW * 0.72
  const y = (v: number) => M.t + (H - M.t - M.b) * (1 - v / max)
  const x0 = (i: number) => M.l + i * bandW
  const baseY = y(0)
  const total = franges.reduce((a, b) => a + b, 0)
  const ticks = Array.from({ length: max / pas + 1 }, (_, k) => k * pas)

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto" role="img"
        aria-label={`Histograma de notes: ${franges.map((n, i) => `${franja(i)}: ${n}`).join(', ')}`}>
        {/* Graella i eix Y (recessius) */}
        {ticks.map(t => (
          <g key={t}>
            <line x1={M.l} x2={W - M.r} y1={y(t)} y2={y(t)} className="stroke-gray-200" strokeWidth={1} />
            <text x={M.l - 6} y={y(t)} dy="0.32em" textAnchor="end" className="fill-gray-500 text-[9px]">{t}</text>
          </g>
        ))}
        {/* Barres */}
        {franges.map((n, i) => {
          const h = baseY - y(n)
          const bx = x0(i) + (bandW - barW) / 2
          const r = Math.min(4, h / 2)
          return (
            <g key={i}>
              {n > 0 && (
                <path className="fill-brand-500"
                  d={`M${bx},${baseY} V${y(n) + r} Q${bx},${y(n)} ${bx + r},${y(n)} H${bx + barW - r} Q${bx + barW},${y(n)} ${bx + barW},${y(n) + r} V${baseY} Z`}
                  opacity={actiu == null || actiu === i ? 1 : 0.55} />
              )}
              <text x={x0(i) + bandW / 2} y={H - M.b + 16} textAnchor="middle" className="fill-gray-500 text-[9px]">
                {franja(i)}
              </text>
              {/* Zona sensible més gran que la barra */}
              <rect x={x0(i)} y={M.t} width={bandW} height={baseY - M.t} fill="transparent"
                tabIndex={0} aria-label={`${franja(i)}: ${n} alumnes`}
                onMouseEnter={() => setActiu(i)} onMouseLeave={() => setActiu(null)}
                onFocus={() => setActiu(i)} onBlur={() => setActiu(null)}
                className="outline-none focus-visible:stroke-gray-400" />
            </g>
          )
        })}
        {/* Llindar d'aprovat */}
        <line x1={x0(5)} x2={x0(5)} y1={M.t - 4} y2={baseY} className="stroke-gray-500" strokeWidth={1.5} strokeDasharray="4 3" />
        <text x={x0(5) + 4} y={M.t + 6} className="fill-gray-600 text-[9px]">aprovat ≥ 5</text>
        <line x1={M.l} x2={W - M.r} y1={baseY} y2={baseY} className="stroke-gray-300" strokeWidth={1} />
      </svg>
      {actiu != null && (
        <div role="tooltip"
          className="absolute -translate-x-1/2 -translate-y-full bg-gray-900 text-white text-xs rounded px-2 py-1 pointer-events-none whitespace-nowrap"
          style={{ left: `${((x0(actiu) + bandW / 2) / W) * 100}%`, top: `${(y(franges[actiu]) / H) * 100}%` }}>
          Nota {franja(actiu)}: <strong>{franges[actiu]}</strong> {franges[actiu] === 1 ? 'alumne' : 'alumnes'}
          {total > 0 && ` (${fmt((franges[actiu] / total) * 100, 1)} %)`}
        </div>
      )}
    </div>
  )
}

function Barra({ percent, etiqueta }: { percent: number; etiqueta: string }) {
  return (
    <div className="flex items-center gap-2" title={etiqueta}>
      <div className="flex-1 h-2.5 bg-gray-100 rounded-full overflow-hidden">
        <div className="h-full bg-brand-500 rounded-full" style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} />
      </div>
      <span className="text-xs tabular-nums text-gray-700 w-12 text-right">{fmt(percent, 1)} %</span>
    </div>
  )
}

function FilaPregunta({ p, entregats }: { p: PreguntaStats; entregats: number }) {
  const [obert, setObert] = useState(false)
  const dificil = !p.bonus && p.percentRendiment != null && p.percentRendiment < RENDIMENT_BAIX
  const opcions = p.opcions ? Object.entries(p.opcions) : []
  return (
    <>
      <tr className="border-t align-top">
        <td className="py-2 pr-3">
          <div className="flex items-start gap-2">
            <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded shrink-0">{p.ordre}</span>
            <div className="min-w-0">
              <p className="text-gray-800 line-clamp-2" title={p.enunciat}>{p.enunciat}</p>
              <div className="flex flex-wrap gap-1.5 mt-1 text-xs">
                <span className="text-gray-400">{p.tipus}</span>
                {p.ra && <span className="bg-indigo-100 text-indigo-700 px-1.5 rounded">{p.ra}</span>}
                {p.bonus && <span className="bg-green-100 text-green-700 px-1.5 rounded">Bonus</span>}
                {dificil && <span className="bg-amber-100 text-amber-800 px-1.5 rounded">⚠ Rendiment baix</span>}
                {opcions.length > 0 && entregats > 0 && (
                  <button onClick={() => setObert(o => !o)} aria-expanded={obert}
                    className="text-brand-600 hover:underline print:hidden">
                    {obert ? 'Amaga les opcions' : 'Veure les opcions triades'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </td>
        <td className="py-2 pr-3 tabular-nums">{fmt(p.punts)}</td>
        <td className="py-2 pr-3">
          {p.percentRendiment != null
            ? <Barra percent={p.percentRendiment} etiqueta={`Mitjana: ${fmt(p.mitjanaPunts)} de ${fmt(p.punts)} punts`} />
            : <span className="text-xs text-gray-400">—</span>}
        </td>
        <td className="py-2 pr-3 text-right tabular-nums whitespace-nowrap">{p.percentCorrectes != null ? `${fmt(p.percentCorrectes, 1)} %` : '—'}</td>
        <td className="py-2 text-right tabular-nums">{p.senseResposta}</td>
      </tr>
      {obert && opcions.length > 0 && (
        <tr>
          <td colSpan={5} className="pb-3 pl-9">
            <div className="space-y-1.5 max-w-md">
              {opcions.map(([lletra, n]) => {
                const pct = entregats > 0 ? (n / entregats) * 100 : 0
                const correcta = lletra === p.correcta
                return (
                  <div key={lletra} className="flex items-center gap-2 text-xs">
                    <span className={`w-20 shrink-0 ${correcta ? 'font-semibold text-gray-900' : 'text-gray-600'}`}>
                      {lletra.toUpperCase()}) {correcta && '✓ correcta'}
                    </span>
                    <div className="flex-1 h-2.5 bg-gray-100 rounded-full overflow-hidden">
                      <div className="h-full bg-brand-500 rounded-full" style={{ width: `${pct}%` }} />
                    </div>
                    <span className="w-24 text-right tabular-nums text-gray-700">{n} ({fmt(pct, 1)} %)</span>
                  </div>
                )
              })}
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

/** Crea un grup amb els suspesos (i, si es vol, els no presentats) per programar-hi la recuperació. */
function PanellRecuperacio({ examId, titol }: { examId: string; titol: string }) {
  const [dades, setDades] = useState<Recuperacio | null>(null)
  const [triats, setTriats] = useState<Set<string>>(new Set())
  const [nom, setNom] = useState(`Recuperació ${titol}`.slice(0, 255))
  const [carregant, setCarregant] = useState(false)
  const [creant, setCreant] = useState(false)
  const [error, setError] = useState('')
  const [creat, setCreat] = useState<Grup | null>(null)

  const obre = async () => {
    setCarregant(true); setError('')
    try {
      const d = await getRecuperacio(examId)
      setDades(d)
      // Per defecte, els suspesos; els no presentats s'hi afegeixen a mà
      setTriats(new Set(d.candidats.filter(c => c.motiu === 'SUSPES').map(c => c.alumneId)))
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'han pogut carregar els alumnes')
    } finally { setCarregant(false) }
  }

  const commuta = (id: string) => setTriats(prev => {
    const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n
  })

  const crea = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!nom.trim() || triats.size === 0) return
    setCreant(true); setError('')
    try {
      setCreat(await crearGrupRecuperacio(examId, nom.trim(), [...triats]))
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut crear el grup')
    } finally { setCreant(false) }
  }

  return (
    <section className="bg-white border rounded-xl p-5 space-y-3 print:hidden">
      <h2 className="text-sm font-semibold text-gray-800">Recuperació</h2>
      {creat ? (
        <div className="text-sm space-y-2">
          <p className="text-green-800">✓ S'ha creat el grup <strong>{creat.name}</strong> amb {creat.students.length} {creat.students.length === 1 ? 'alumne' : 'alumnes'}.</p>
          <p className="text-gray-600">
            Ara, a <Link to="/professor/exams" className="text-brand-600 hover:underline">Els meus exàmens</Link>:
            {' '}<strong>Duplicar</strong> l'examen (o crear-ne un de nou), canviar-li el títol a <strong>Previsualitzar</strong> i
            {' '}<strong>Programar</strong>-lo per a aquest grup. Només el podran fer els alumnes del grup.
          </p>
        </div>
      ) : !dades ? (
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-sm text-gray-600">Crea un grup amb els alumnes suspesos per programar-los la recuperació.</p>
          <button onClick={obre} disabled={carregant}
            className="text-sm border border-brand-600 text-brand-600 px-4 py-1.5 rounded-lg hover:bg-brand-50 disabled:opacity-50">
            {carregant ? 'Carregant…' : 'Crear grup amb els suspesos'}
          </button>
        </div>
      ) : dades.candidats.length === 0 ? (
        <p className="text-sm text-green-800">✓ No hi ha cap alumne suspès ni cap no presentat.</p>
      ) : (
        <form onSubmit={crea} className="space-y-3">
          {dades.respostesPendents > 0 && (
            <p className="text-sm bg-amber-50 border border-amber-200 text-amber-900 rounded-lg px-3 py-2">
              ⚠ Hi ha {dades.respostesPendents} respostes sense nota: les notes marcades com a provisionals encara poden pujar.
            </p>
          )}
          <ul className="divide-y border rounded-lg">
            {dades.candidats.map(c => (
              <li key={c.alumneId}>
                <label className="flex items-center gap-3 px-3 py-2 text-sm cursor-pointer hover:bg-gray-50">
                  <input type="checkbox" checked={triats.has(c.alumneId)} onChange={() => commuta(c.alumneId)}
                    className="accent-brand-600 w-4 h-4" />
                  <span className="flex-1 min-w-0">
                    <span className="text-gray-800">{c.nom}</span>
                    <span className="text-xs text-gray-400 ml-2">{c.email}</span>
                  </span>
                  {c.motiu === 'SUSPES' ? (
                    <span className="text-xs tabular-nums text-gray-700">
                      {fmt(c.nota)}{c.notaProvisional && <span className="text-amber-700"> (provisional)</span>}
                    </span>
                  ) : (
                    <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">No presentat</span>
                  )}
                </label>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 flex-1 min-w-[14rem]">
              <span className="text-xs text-gray-500">Nom del grup</span>
              <input value={nom} onChange={e => setNom(e.target.value)} maxLength={255} required
                className="border rounded-lg px-3 py-1.5 text-sm" />
            </label>
            <button type="submit" disabled={creant || triats.size === 0 || !nom.trim()}
              className="text-sm bg-brand-600 text-white px-4 py-2 rounded-lg hover:bg-brand-700 disabled:opacity-50">
              {creant ? 'Creant…' : `Crear el grup (${triats.size})`}
            </button>
          </div>
          <p className="text-xs text-gray-400">Nota sobre 10. Els no presentats són els que no van entregar l'examen; afegeix-los si també han de fer la recuperació.</p>
        </form>
      )}
      {error && <p className="text-sm text-red-600">{error}</p>}
    </section>
  )
}

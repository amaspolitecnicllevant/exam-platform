import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getHistorial } from '../../api/sessions'
import type { HistorialItem } from '../../types'

const fmt = (n: number | null | undefined) =>
  n == null ? '—' : Number(n.toFixed(2)).toString().replace('.', ',')

const data = (iso?: string, llarga = false) =>
  iso ? new Date(iso).toLocaleDateString('ca-ES', llarga ? { dateStyle: 'medium' } : { day: 'numeric', month: 'short' }) : '—'

const mitjana = (notes: number[]) => notes.length ? notes.reduce((a, b) => a + b, 0) / notes.length : null

interface Grup { clau: string; nom: string; items: HistorialItem[] }

export default function HistorialPage() {
  const [items, setItems] = useState<HistorialItem[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getHistorial().then(setItems).catch(() => setError('No s\'ha pogut carregar l\'historial'))
  }, [])

  // Un grup per mòdul, amb els exàmens en ordre cronològic (el backend ja els ordena)
  const grups = useMemo<Grup[]>(() => {
    const m = new Map<string, Grup>()
    for (const it of items ?? []) {
      const clau = it.modulId ?? 'sense-modul'
      if (!m.has(clau)) m.set(clau, { clau, nom: it.modulNom ?? 'Sense mòdul', items: [] })
      m.get(clau)!.items.push(it)
    }
    return [...m.values()].sort((a, b) => a.nom.localeCompare(b.nom, 'ca'))
  }, [items])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!items) return <Layout><p className="text-gray-400 text-sm">Carregant…</p></Layout>

  const publicades = items.filter(i => i.nota != null).map(i => i.nota as number)
  const aprovats = publicades.filter(n => n >= 5).length

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-brand-700">Historial i notes</h1>
          <p className="text-sm text-gray-500">Tots els exàmens que has entregat. La nota apareix quan el professor la publica.</p>
        </div>

        {items.length === 0 ? (
          <p className="text-sm text-gray-500 bg-white border rounded-xl p-6">Encara no has entregat cap examen.</p>
        ) : (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <Tile label="Exàmens entregats" valor={`${items.length}`} />
              <Tile label="Amb nota publicada" valor={`${publicades.length}`} />
              <Tile label="Nota mitjana" valor={fmt(mitjana(publicades))} detall={publicades.length ? '/ 10' : 'encara cap nota'} />
              <Tile label="Aprovats" valor={publicades.length ? `${aprovats} de ${publicades.length}` : '—'} />
            </div>

            {grups.map(g => <TargetaModul key={g.clau} grup={g} />)}
          </>
        )}
      </div>
    </Layout>
  )
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

function TargetaModul({ grup }: { grup: Grup }) {
  const ambNota = grup.items.filter(i => i.nota != null)
  const mitjanaModul = mitjana(ambNota.map(i => i.nota as number))
  return (
    <section className="bg-white border rounded-xl p-5 space-y-4" aria-labelledby={`modul-${grup.clau}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id={`modul-${grup.clau}`} className="font-semibold text-gray-800">{grup.nom}</h2>
        <p className="text-sm text-gray-600">
          Mitjana del mòdul: <strong className="tabular-nums">{fmt(mitjanaModul)}</strong>
          {ambNota.length > 0 && <span className="text-gray-400"> ({ambNota.length} {ambNota.length === 1 ? 'nota' : 'notes'})</span>}
        </p>
      </div>

      {ambNota.length >= 2 && <Evolucio items={ambNota} />}

      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="text-left text-xs text-gray-500">
            <th className="py-1.5 pr-3 font-medium">Examen</th>
            <th className="py-1.5 pr-3 font-medium">Entregat</th>
            <th className="py-1.5 font-medium text-right">Nota</th>
          </tr>
        </thead>
        <tbody>
          {[...grup.items].reverse().map(it => (
            <tr key={it.sessionId} className="border-t">
              <td className="py-2 pr-3">
                <Link to={`/student/sessions/${it.sessionId}/results`} className="text-brand-700 hover:underline">
                  {it.examTitle}
                </Link>
              </td>
              <td className="py-2 pr-3 text-gray-600 whitespace-nowrap">{data(it.submittedAt, true)}</td>
              <td className="py-2 text-right whitespace-nowrap">
                {it.nota != null
                  ? <span className="font-semibold tabular-nums text-gray-900">
                      {fmt(it.nota)} <span className="text-xs font-normal text-gray-400">/ 10</span>
                      {it.nota < 5 && <span className="ml-2 text-xs font-normal text-gray-500">(suspès)</span>}
                    </span>
                  : <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full">Pendent de correcció</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

/** Evolució de les notes publicades d'un mòdul: una sola sèrie, línia d'aprovat al 5, tooltip per punt. */
function Evolucio({ items }: { items: HistorialItem[] }) {
  const [actiu, setActiu] = useState<number | null>(null)
  const W = 640, H = 170, M = { t: 14, r: 16, b: 26, l: 28 }
  const n = items.length
  const x = (i: number) => M.l + (n === 1 ? (W - M.l - M.r) / 2 : (i * (W - M.l - M.r)) / (n - 1))
  const y = (v: number) => M.t + (H - M.t - M.b) * (1 - Math.max(0, Math.min(10, v)) / 10)
  const punts = items.map((it, i) => ({ it, px: x(i), py: y(it.nota as number) }))
  const d = punts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.px},${p.py}`).join(' ')

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto" role="img"
        aria-label={`Evolució de les notes: ${items.map(it => `${it.examTitle} ${fmt(it.nota)}`).join(', ')}`}>
        {[0, 5, 10].map(t => (
          <g key={t}>
            <line x1={M.l} x2={W - M.r} y1={y(t)} y2={y(t)}
              className={t === 5 ? 'stroke-gray-400' : 'stroke-gray-200'} strokeWidth={1}
              strokeDasharray={t === 5 ? '4 3' : undefined} />
            <text x={M.l - 6} y={y(t)} dy="0.32em" textAnchor="end" className="fill-gray-500 text-[9px]">{t}</text>
          </g>
        ))}
        <path d={d} fill="none" className="stroke-brand-500" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
        {punts.map((p, i) => (
          <g key={p.it.sessionId}>
            <circle cx={p.px} cy={p.py} r={actiu === i ? 6 : 4.5} className="fill-brand-500 stroke-white" strokeWidth={2} />
            <text x={p.px} y={H - M.b + 16} className="fill-gray-500 text-[9px]"
              textAnchor={n > 1 && i === 0 ? 'start' : n > 1 && i === n - 1 ? 'end' : 'middle'}>
              {data(p.it.submittedAt)}
            </text>
            {/* Zona sensible més gran que el punt */}
            <circle cx={p.px} cy={p.py} r={14} fill="transparent" tabIndex={0}
              aria-label={`${p.it.examTitle}: ${fmt(p.it.nota)} sobre 10`}
              onMouseEnter={() => setActiu(i)} onMouseLeave={() => setActiu(null)}
              onFocus={() => setActiu(i)} onBlur={() => setActiu(null)} className="outline-none" />
          </g>
        ))}
      </svg>
      <p className="text-xs text-gray-400 mt-1">La línia discontínua marca l'aprovat (5).</p>
      {actiu != null && (
        <div role="tooltip"
          className="absolute -translate-x-1/2 -translate-y-full bg-gray-900 text-white text-xs rounded px-2 py-1 pointer-events-none whitespace-nowrap"
          style={{ left: `${(punts[actiu].px / W) * 100}%`, top: `calc(${(punts[actiu].py / H) * 100}% - 8px)` }}>
          {punts[actiu].it.examTitle} · {data(punts[actiu].it.submittedAt, true)}: <strong>{fmt(punts[actiu].it.nota)}</strong>
        </div>
      )}
    </div>
  )
}

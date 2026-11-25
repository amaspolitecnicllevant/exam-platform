import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getResumNotes, type ResumExamen, type ResumUnitat } from '../../api/exams'
import { NOTA_APROVAT } from '../../notes'

const fmt = (n: number | null) => n == null ? '—' : n.toFixed(2).replace('.', ',')
const pct = (n: number | null) => n == null ? '—' : `${Math.round(n)} %`

function Mitjana({ valor }: { valor: number | null }) {
  const suspesa = valor != null && valor < NOTA_APROVAT
  return <span className={`tabular-nums font-semibold ${valor == null ? 'text-gray-300' : suspesa ? 'text-red-600' : 'text-gray-800'}`}>{fmt(valor)}</span>
}

/** Alumnes aprovats i nota mitjana de cada examen, i mitjana de cada UT (només alumnes que han entregat). */
export default function ResumNotesPage() {
  const [dades, setDades] = useState<{ examens: ResumExamen[]; unitats: ResumUnitat[] } | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getResumNotes().then(setDades).catch(err => setError(err?.response?.data?.error || 'No s\'ha pogut carregar el resum'))
  }, [])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!dades) return <Layout><p className="text-gray-400 text-sm">Carregant…</p></Layout>

  const examens = dades.examens.filter(e => e.entregats > 0)
  const sensePendents = dades.examens.length - examens.length

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-brand-700">Resum de notes</h1>
          <p className="text-sm text-gray-500">
            Alumnes aprovats (nota ≥ {NOTA_APROVAT}) i nota mitjana sobre 10, comptant només els alumnes que han entregat.
          </p>
        </div>

        <section className="bg-white border rounded-xl p-5 space-y-3">
          <h2 className="text-sm font-semibold text-gray-800">Mitjana per UT</h2>
          {dades.unitats.length === 0 ? (
            <p className="text-xs text-gray-500">
              Cap examen té UT. Indica-la a cada examen (Previsualitzar → «Unitat de treball») per veure'n la mitjana aquí.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-left text-xs text-gray-500">
                    <th className="py-2 pr-3 font-medium">UT</th>
                    <th className="py-2 pr-3 font-medium">Mòdul</th>
                    <th className="py-2 pr-3 font-medium text-right">Exàmens</th>
                    <th className="py-2 pr-3 font-medium text-right">Entregats</th>
                    <th className="py-2 pr-3 font-medium text-right">Aproven</th>
                    <th className="py-2 font-medium text-right">Nota mitjana</th>
                  </tr>
                </thead>
                <tbody>
                  {dades.unitats.map(u => (
                    <tr key={`${u.modulNom}|${u.ut}`} className="border-t">
                      <td className="py-1.5 pr-3 font-mono">{u.ut}</td>
                      <td className="py-1.5 pr-3 text-gray-600">{u.modulNom ?? '—'}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{u.examens}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{u.entregats}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{u.aprovats} <span className="text-xs text-gray-400">({pct(u.percentAprovats)})</span></td>
                      <td className="py-1.5 text-right"><Mitjana valor={u.mitjana} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="bg-white border rounded-xl p-5 space-y-3">
          <h2 className="text-sm font-semibold text-gray-800">Per examen</h2>
          {examens.length === 0 ? (
            <p className="text-xs text-gray-500">Cap alumne ha entregat encara cap examen.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-left text-xs text-gray-500">
                    <th className="py-2 pr-3 font-medium">Examen</th>
                    <th className="py-2 pr-3 font-medium">UT</th>
                    <th className="py-2 pr-3 font-medium text-right">Entregats</th>
                    <th className="py-2 pr-3 font-medium text-right">Aproven</th>
                    <th className="py-2 pr-3 font-medium text-right">Suspesos</th>
                    <th className="py-2 font-medium text-right">Nota mitjana</th>
                  </tr>
                </thead>
                <tbody>
                  {examens.map(e => (
                    <tr key={e.id} className="border-t">
                      <td className="py-1.5 pr-3"><Link to={`/professor/exams/${e.id}/notes`} className="text-brand-700 hover:underline">{e.titol}</Link></td>
                      <td className="py-1.5 pr-3 font-mono text-gray-600">{e.ut ?? '—'}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{e.entregats}</td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{e.aprovats} <span className="text-xs text-gray-400">({pct(e.percentAprovats)})</span></td>
                      <td className="py-1.5 pr-3 text-right tabular-nums">{e.entregats - e.aprovats}</td>
                      <td className="py-1.5 text-right"><Mitjana valor={e.mitjana} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {sensePendents > 0 && <p className="text-xs text-gray-400">{sensePendents} examen(s) sense cap entrega no surten a la taula.</p>}
        </section>
      </div>
    </Layout>
  )
}

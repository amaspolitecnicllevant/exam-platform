import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getProgres } from '../../api/sessions'
import { consellsPerTipus } from '../../utils/consellsRecuperacio'
import type { Progres } from '../../types'

const fmt = (n: number | null | undefined) =>
  n == null ? '—' : Number(n.toFixed(2)).toString().replace('.', ',')

const data = (iso?: string) => iso ? new Date(iso).toLocaleDateString('ca-ES', { dateStyle: 'medium' }) : ''

/**
 * Què cal recuperar: per mòdul i competència, el que s'ha suspès en els diferents exàmens (amb les notes publicades),
 * agrupat per estudiar-ho tot d'una vegada.
 */
export default function ProgresPage() {
  const [progres, setProgres] = useState<Progres | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getProgres().then(setProgres).catch(() => setError('No s\'ha pogut carregar el progrés'))
  }, [])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!progres) return <Layout><p className="text-gray-400 text-sm">Carregant…</p></Layout>

  return (
    <Layout>
      <div className="space-y-6 max-w-3xl">
        <div>
          <h1 className="text-xl font-bold text-gray-800">El meu progrés</h1>
          <p className="text-sm text-gray-500 mt-1">
            Competències que tens suspeses en algun examen, amb les preguntes on has perdut punts, agrupades entre tots els exàmens
            amb les notes publicades. Les preguntes sense corregir encara no hi compten.
          </p>
        </div>

        {progres.moduls.length === 0 && (
          <div className="bg-green-50 border border-green-200 rounded-xl px-5 py-4 text-sm text-green-800">
            ✓ No tens cap competència suspesa als exàmens publicats.
          </div>
        )}

        {progres.moduls.map(m => (
          <section key={m.modulId ?? 'sense-modul'} className="space-y-4">
            <h2 className="font-semibold text-brand-800 border-b border-brand-200 pb-1">{m.modulNom ?? 'Sense mòdul'}</h2>
            {m.competencies.map(c => {
              const consells = consellsPerTipus(c.suspesos.flatMap(e => e.preguntes.map(p => p.tipus)))
              return (
                <div key={c.codi} className="bg-white border rounded-xl p-5 space-y-3">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="font-semibold text-indigo-700">Competència {c.codi}</p>
                    <p className="text-xs text-gray-500">
                      Nota global: <span className="font-semibold tabular-nums">{fmt(c.notaGlobal)}</span> / 10 · suspesa a {c.suspesos.length} {c.suspesos.length === 1 ? 'examen' : 'exàmens'}
                    </p>
                  </div>

                  {c.suspesos.map(e => (
                    <div key={e.sessionId} className="bg-gray-50 border rounded-lg px-4 py-3 space-y-2">
                      <div className="flex items-baseline justify-between gap-3">
                        <Link to={`/student/sessions/${e.sessionId}/results`} className="text-sm font-medium text-brand-700 hover:underline">
                          {e.titol}
                        </Link>
                        <span className="text-xs text-red-600 tabular-nums">{fmt(e.nota)} / 10 {e.data && <span className="text-gray-400">· {data(e.data)}</span>}</span>
                      </div>
                      <ul className="text-sm text-gray-700 space-y-1">
                        {e.preguntes.map(p => (
                          <li key={p.numero} className="flex gap-2">
                            <span className="text-xs bg-brand-100 text-brand-700 px-2 py-0.5 rounded h-fit">P{p.numero}</span>
                            <span className="flex-1">{p.enunciat}</span>
                            <span className="text-xs text-gray-400 tabular-nums whitespace-nowrap">{fmt(p.obtinguts)} / {fmt(p.punts)}</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}

                  <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
                    <p className="text-xs font-semibold text-amber-800 uppercase tracking-wide mb-1">Com recuperar-la</p>
                    <ul className="list-disc pl-5 text-sm text-gray-700 space-y-1">
                      <li>Estudia els continguts de les preguntes de dalt de tots els exàmens alhora: és el que cal per recuperar {c.codi}.</li>
                      {consells.map(t => <li key={t}>{t}</li>)}
                      <li>Fes un esquema de {c.codi} i comprova que pots explicar-lo sense mirar els apunts.</li>
                    </ul>
                  </div>
                </div>
              )
            })}
          </section>
        ))}
      </div>
    </Layout>
  )
}

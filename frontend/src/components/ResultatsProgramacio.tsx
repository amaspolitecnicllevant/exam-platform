import { useState } from 'react'
import { NOTA_APROVAT } from '../notes'
import type { NotaAgrupada, ResultatsModul } from '../types'

const fmt = (n: number | null | undefined, dec = 2) =>
  n == null ? '—' : n.toFixed(dec).replace('.', ',')

/** Punts reals sobre el màxim i l'equivalent sobre 10; en vermell si no arriba a l'aprovat. */
function Nota({ nota }: { nota: NotaAgrupada | null }) {
  if (!nota) return <span className="text-gray-400 text-sm">Cap alumne ha entregat encara</span>
  const suspesa = nota.sobreDeu != null && nota.sobreDeu < NOTA_APROVAT
  return (
    <span className="tabular-nums text-sm">
      <span className={`font-semibold ${suspesa ? 'text-red-600' : 'text-gray-900'}`}>
        {fmt(nota.mitjanaPunts)} <span className="font-normal text-gray-400">/ {fmt(nota.maxim)} pts</span>
      </span>
      <span className={`ml-2 ${suspesa ? 'text-red-600' : 'text-gray-500'}`}>{fmt(nota.sobreDeu)} sobre 10</span>
    </span>
  )
}

function Avis({ resultats }: { resultats: ResultatsModul }) {
  if (resultats.examens === 0) {
    return <p className="text-xs text-gray-400">Encara no hi ha cap examen entregat d'aquest mòdul.</p>
  }
  if (resultats.respostesPendents > 0) {
    return (
      <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-1.5">
        Provisional: hi ha {resultats.respostesPendents} respostes sense qualificar; compten 0 fins que es corregeixin.
      </p>
    )
  }
  return null
}

/**
 * Sota les unitats de treball: la nota real dels exàmens i pràctiques de cada unitat (mitjana dels alumnes que han
 * entregat), amb un botó per desglossar-la per competència.
 */
export function ResultatsUnitats({ resultats }: { resultats: ResultatsModul }) {
  const [oberts, setOberts] = useState<Set<string>>(new Set())
  const alterna = (codi: string) => setOberts(prev => {
    const n = new Set(prev); n.has(codi) ? n.delete(codi) : n.add(codi); return n
  })
  if (resultats.unitats.length === 0) return null
  return (
    <section className="bg-white border border-gray-200 rounded-xl p-4 space-y-3">
      <div>
        <h2 className="font-semibold text-gray-800">Notes dels exàmens per unitat</h2>
        <p className="text-xs text-gray-500">
          Mitjana dels alumnes que han entregat, en punts reals. La nota de la unitat agrupa els seus exàmens i pràctiques
          segons els punts que té cada un.
        </p>
      </div>
      <Avis resultats={resultats} />
      <div className="space-y-3">
        {resultats.unitats.map(u => (
          <div key={u.codi} className="border border-gray-100 rounded-lg p-3 space-y-2">
            <div className="flex items-baseline justify-between gap-2 flex-wrap">
              <p className="text-sm"><span className="font-mono font-semibold">{u.codi}</span>{u.nom ? ` · ${u.nom}` : ''}</p>
              <Nota nota={u.nota} />
            </div>
            {u.examens.length === 0
              ? <p className="text-xs text-gray-400">Cap examen entregat d'aquesta unitat.</p>
              : (
                <ul className="text-sm space-y-0.5">
                  {u.examens.map(e => (
                    <li key={e.examId} className="flex items-baseline justify-between gap-2 flex-wrap">
                      <span className="text-gray-700">{e.titol} <span className="text-xs text-gray-400">({e.entregats} {e.entregats === 1 ? 'alumne' : 'alumnes'})</span></span>
                      <Nota nota={e.nota} />
                    </li>
                  ))}
                </ul>
              )}
            {u.competencies.length > 0 && (
              <>
                <button type="button" onClick={() => alterna(u.codi)} aria-expanded={oberts.has(u.codi)}
                  className="text-xs text-brand-600 hover:underline">
                  {oberts.has(u.codi) ? 'Amaga el desglossament' : 'Desglossa per competència'}
                </button>
                {oberts.has(u.codi) && (
                  <ul className="text-sm space-y-0.5 pl-3 border-l-2 border-indigo-100">
                    {u.competencies.map(c => (
                      <li key={c.codi} className="flex items-baseline justify-between gap-2 flex-wrap">
                        <span className="bg-indigo-100 text-indigo-700 text-xs px-2 py-0.5 rounded font-medium">{c.codi}</span>
                        <Nota nota={c.nota} />
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}

/**
 * Sota les competències: la nota mitjana de cada competència (agrupant tots els exàmens que la porten), amb un botó
 * per desglossar-la per unitat i examen o pràctica, indicant el % de punts aconseguits i el pes de cada examen.
 */
export function ResultatsCompetencies({ resultats }: { resultats: ResultatsModul }) {
  const [oberts, setOberts] = useState<Set<string>>(new Set())
  const alterna = (codi: string) => setOberts(prev => {
    const n = new Set(prev); n.has(codi) ? n.delete(codi) : n.add(codi); return n
  })
  if (resultats.competencies.length === 0) return null
  return (
    <section className="bg-white border border-gray-200 rounded-xl p-4 space-y-3">
      <div>
        <h2 className="font-semibold text-gray-800">Nota mitjana per competència</h2>
        <p className="text-xs text-gray-500">
          Agrupa les preguntes de cada competència de tots els exàmens i pràctiques del mòdul. En vermell si equival a menys de {NOTA_APROVAT} sobre 10.
        </p>
      </div>
      <Avis resultats={resultats} />
      <div className="space-y-3">
        {resultats.competencies.map(c => (
          <div key={c.codi} className="border border-gray-100 rounded-lg p-3 space-y-2">
            <div className="flex items-baseline justify-between gap-2 flex-wrap">
              <p className="text-sm" title={c.descripcio || undefined}>
                <span className="font-mono font-semibold text-indigo-700">{c.codi}</span>
                {c.descripcio ? <span className="text-gray-500"> · {c.descripcio}</span> : null}
              </p>
              <Nota nota={c.nota} />
            </div>
            {c.perUnitat.length > 0 && (
              <>
                <button type="button" onClick={() => alterna(c.codi)} aria-expanded={oberts.has(c.codi)}
                  className="text-xs text-brand-600 hover:underline">
                  {oberts.has(c.codi) ? 'Amaga el desglossament' : 'Desglossa per examen o pràctica de cada unitat'}
                </button>
                {oberts.has(c.codi) && (
                  <div className="space-y-2 pl-3 border-l-2 border-indigo-100">
                    {c.perUnitat.map(u => (
                      <div key={u.codi ?? 'sense'} className="space-y-1">
                        <p className="text-xs font-semibold text-gray-600">
                          {u.codi ? <><span className="font-mono">{u.codi}</span>{u.nom ? ` · ${u.nom}` : ''}</> : u.nom}
                        </p>
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="text-left text-xs text-gray-400">
                              <th className="font-medium pr-3">Examen o pràctica</th>
                              <th className="font-medium pr-3 text-right">Punts</th>
                              <th className="font-medium pr-3 text-right" title="% dels punts d'aquesta competència que han tret">Aconseguit</th>
                              <th className="font-medium text-right" title="% que representa aquest examen dins la nota de la competència a la unitat">Pes</th>
                            </tr>
                          </thead>
                          <tbody>
                            {u.examens.map(e => {
                              const suspes = e.nota.sobreDeu != null && e.nota.sobreDeu < NOTA_APROVAT
                              return (
                                <tr key={e.examId} className="border-t border-gray-50">
                                  <td className="pr-3 py-0.5 text-gray-700">{e.titol} <span className="text-xs text-gray-400">({e.entregats})</span></td>
                                  <td className={`pr-3 text-right tabular-nums ${suspes ? 'text-red-600 font-semibold' : ''}`}>
                                    {fmt(e.nota.mitjanaPunts)} <span className="text-xs font-normal text-gray-400">/ {fmt(e.nota.maxim)}</span>
                                  </td>
                                  <td className={`pr-3 text-right tabular-nums ${suspes ? 'text-red-600 font-semibold' : ''}`}>{fmt(e.percentRendiment, 1)} %</td>
                                  <td className="text-right tabular-nums text-gray-600">{fmt(e.percentPes, 1)} %</td>
                                </tr>
                              )
                            })}
                          </tbody>
                        </table>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        ))}
      </div>
    </section>
  )
}

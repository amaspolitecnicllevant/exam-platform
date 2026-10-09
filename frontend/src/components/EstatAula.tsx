import { useCallback, useEffect, useMemo, useState } from 'react'
import { esborraEquip, fixaReferenciaEquip, getEquipsAula, type Equip, type EquipsAula, type EstatEquip } from '../api/equips'
import { useAuth } from '../context/AuthContext'

const ORDRE: Record<EstatEquip, number> = { ALTERAT: 0, SENSE_PLATAFORMA: 1, SENSE_NOTICIES: 2, SENSE_REFERENCIA: 3, PREPARAT: 4 }

/** «fa 5 min», «fa 3 h», «fa 8 dies» */
function fa(iso: string): string {
  const min = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60_000))
  if (min < 60) return `fa ${min} min`
  if (min < 48 * 60) return `fa ${Math.round(min / 60)} h`
  return `fa ${Math.round(min / 1440)} dies`
}

function etiqueta(e: Equip): { text: string; classe: string } {
  switch (e.estat) {
    case 'PREPARAT':         return { text: 'Preparat', classe: 'bg-green-100 text-green-800' }
    case 'ALTERAT':          return { text: 'Alterat', classe: 'bg-red-100 text-red-800' }
    case 'SENSE_PLATAFORMA': return { text: 'No arriba a la plataforma', classe: 'bg-red-100 text-red-800' }
    case 'SENSE_REFERENCIA': return { text: 'Sense referència', classe: 'bg-blue-100 text-blue-800' }
    case 'SENSE_NOTICIES':
      return e.faTemps
        ? { text: `Fa ${e.diesSenseInformar} dies que no s'encén`, classe: 'bg-amber-100 text-amber-900 font-semibold' }
        : { text: 'Apagat o sense informar', classe: 'bg-gray-100 text-gray-700' }
  }
}

/**
 * Estat dels ordinadors d'una aula, segons els informes que ells mateixos envien.
 * - `resum`: una línia d'avís (per a professors, a la llista d'exàmens); els detalls es desplegen.
 * - Sense `resum`: la taula completa. Els administradors poden fixar la referència i esborrar ordinadors.
 */
export default function EstatAula({ aulaId, resum = false }: { aulaId: string; resum?: boolean }) {
  const { user } = useAuth()
  const esAdmin = user?.role === 'ADMIN'
  const [dades, setDades] = useState<EquipsAula | null>(null)
  const [error, setError] = useState('')
  const [obert, setObert] = useState(!resum)
  const [detall, setDetall] = useState<string | null>(null)

  const carrega = useCallback(() => {
    getEquipsAula(aulaId).then(d => { setDades(d); setError('') })
      .catch(() => setError('No s\'ha pogut consultar l\'estat dels ordinadors.'))
  }, [aulaId])

  useEffect(() => {
    carrega()
    const id = setInterval(carrega, 60_000)
    return () => clearInterval(id)
  }, [carrega])

  const equips = useMemo(
    () => [...(dades?.equips ?? [])].sort((a, b) =>
      Number(b.faTemps) - Number(a.faTemps) || ORDRE[a.estat] - ORDRE[b.estat] || a.nom.localeCompare(b.nom)),
    [dades])

  if (error) return <p className="text-xs text-gray-500">{error}</p>
  if (!dades) return null

  const total = dades.equips.length
  const faTemps = equips.filter(e => e.faTemps)

  const fixa = async (e: Equip) => {
    if (!confirm(`Tots els altres ordinadors es compararan amb «${e.nom}».\nNomés ho facis si està net i validat. Vols continuar?`)) return
    try { await fixaReferenciaEquip(e.id); carrega() } catch (err: any) { setError(err?.response?.data?.error || 'No s\'ha pogut fixar la referència.') }
  }
  const esborra = async (e: Equip) => {
    if (!confirm(`Esborrar «${e.nom}» de la llista? Tornarà a sortir si envia un informe.`)) return
    try { await esborraEquip(e.id); carrega() } catch (err: any) { setError(err?.response?.data?.error || 'No s\'ha pogut esborrar.') }
  }

  // ── Línia d'avís (professors, a la targeta de l'examen) ──
  const avis = (
    <div className="flex items-start gap-2 flex-wrap text-xs">
      {total === 0 && <span className="text-gray-500">Cap ordinador d'aquesta aula ha informat encara del seu estat.</span>}
      {faTemps.length > 0 && (
        <span role="alert" className="bg-amber-50 border border-amber-300 text-amber-900 rounded-lg px-3 py-2">
          ⚠ <strong>{faTemps.length} {faTemps.length === 1 ? 'ordinador' : 'ordinadors'}</strong> fa temps que no {faTemps.length === 1 ? 's\'encén' : 's\'encenen'}
          {' '}({faTemps.slice(0, 6).map(e => e.nom).join(', ')}{faTemps.length > 6 ? '…' : ''}).
          {' '}<strong>Demana que els encenguin</strong> abans de l'examen, per poder comprovar que funcionen.
        </span>
      )}
      {dades.alterats > 0 && (
        <span className="bg-red-50 border border-red-300 text-red-900 rounded-lg px-3 py-2">
          ✕ {dades.alterats} {dades.alterats === 1 ? 'ordinador té' : 'ordinadors tenen'} la configuració alterada.
        </span>
      )}
      {equips.some(e => e.estat === 'SENSE_PLATAFORMA') && (
        <span className="bg-red-50 border border-red-300 text-red-900 rounded-lg px-3 py-2">
          ✕ Algun ordinador no arriba a la plataforma.
        </span>
      )}
      {total > 0 && faTemps.length === 0 && dades.alterats === 0 && dades.preparats === total && (
        <span className="text-green-700">✓ Tots els ordinadors ({total}) estan preparats.</span>
      )}
      {total > 0 && (
        <button type="button" onClick={() => setObert(o => !o)} className="text-brand-600 hover:underline self-center">
          {obert ? 'Amagar els ordinadors' : `Veure els ${total} ordinadors`}
        </button>
      )}
    </div>
  )

  const taula = total > 0 && obert && (
    <div className="overflow-x-auto border rounded-lg bg-white">
      <table className="w-full text-left text-xs">
        <thead className="bg-gray-50 text-gray-500">
          <tr>
            <th className="px-3 py-2 font-medium">Ordinador</th>
            <th className="px-3 py-2 font-medium">IP</th>
            <th className="px-3 py-2 font-medium">Estat</th>
            <th className="px-3 py-2 font-medium">Última notícia</th>
            <th className="px-3 py-2 font-medium">Detalls</th>
            {esAdmin && <th className="px-3 py-2" />}
          </tr>
        </thead>
        <tbody>
          {equips.map(e => {
            const et = etiqueta(e)
            const dins = detall === e.id
            const teDetall = e.avisos.length > 0 || e.diferencies.length > 0 || e.navegador
            return (
              <tr key={e.id} className="border-t border-gray-100 align-top">
                <td className="px-3 py-2 font-mono text-gray-800">{e.nom}</td>
                <td className="px-3 py-2 font-mono text-gray-500">{e.ip}</td>
                <td className="px-3 py-2"><span className={`px-2 py-0.5 rounded-full ${et.classe}`}>{et.text}</span></td>
                <td className="px-3 py-2 text-gray-600 whitespace-nowrap">{fa(e.darrerInforme)}</td>
                <td className="px-3 py-2 text-gray-600">
                  {e.avisos.length > 0 && <span className="text-amber-700">{e.avisos.length} {e.avisos.length === 1 ? 'avís' : 'avisos'} · </span>}
                  {teDetall && (
                    <button type="button" onClick={() => setDetall(dins ? null : e.id)} className="text-brand-600 hover:underline">
                      {dins ? 'Amagar' : 'Veure'}
                    </button>
                  )}
                  {dins && (
                    <div className="mt-1 space-y-1">
                      {e.avisos.map((a, i) => <p key={i} className="text-amber-700">⚠ {a}</p>)}
                      {e.diferencies.length > 0 && (
                        <pre className="bg-gray-50 border rounded p-2 text-[11px] whitespace-pre-wrap break-all max-h-40 overflow-auto">{e.diferencies.join('\n')}</pre>
                      )}
                      <p className="text-gray-500">
                        {e.navegador ?? 'Sense navegador'}
                        {e.discLliureMb != null && ` · ${Math.round(e.discLliureMb / 1024)} GB lliures`}
                        {e.arribaIsard != null && ` · Isard: ${e.arribaIsard ? 'sí' : 'no'}`}
                        {e.arribaPlataforma != null && ` · plataforma: ${e.arribaPlataforma ? 'sí' : 'no'}`}
                      </p>
                    </div>
                  )}
                </td>
                {esAdmin && (
                  <td className="px-3 py-2 whitespace-nowrap">
                    <button type="button" onClick={() => fixa(e)} className="text-brand-600 hover:underline mr-3">Fixar com a referència</button>
                    <button type="button" onClick={() => esborra(e)} className="text-red-600 hover:underline">Esborrar</button>
                  </td>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )

  return (
    <div className="space-y-2">
      {!resum && (
        <p className="text-xs text-gray-500">
          {dades.referencia
            ? `Referència: ${dades.referencia.origenNom ?? '—'}, fixada ${fa(dades.referencia.fixadaEl)}.`
            : esAdmin ? 'Encara no hi ha cap ordinador de referència: fixa\'n un (net i validat) perquè es puguin comparar els altres.'
                      : 'Encara no hi ha cap ordinador de referència.'}
          {' '}Informen cada 15 minuts; si fa més de 45 minuts que no ho fan, surten com a apagats.
        </p>
      )}
      {avis}
      {taula}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAlertesSeguretat, type Alerta, type GravetatAlerta } from '../api/alertes'

const ESTIL: Record<GravetatAlerta, { barra: string; punt: string; etiqueta: string }> = {
  ALTA:    { barra: 'bg-red-700 text-white',     punt: 'bg-red-100 text-red-800',     etiqueta: 'Alta' },
  MITJANA: { barra: 'bg-amber-500 text-white',   punt: 'bg-amber-100 text-amber-900', etiqueta: 'Mitjana' },
  INFO:    { barra: 'bg-sky-600 text-white',     punt: 'bg-sky-100 text-sky-900',     etiqueta: 'Informació' },
}

function fa(iso: string | null): string {
  if (!iso) return ''
  const min = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60_000))
  if (min < 60) return `fa ${min} min`
  if (min < 48 * 60) return `fa ${Math.round(min / 60)} h`
  return `fa ${Math.round(min / 1440)} dies`
}

/**
 * Avís de seguretat per als administradors, a totes les pàgines: intents de contrasenya, comptes bloquejats, un administrador
 * des d'una adreça nova, ordinadors alterats… Es calcula al servidor a partir de l'auditoria i s'actualitza cada 5 minuts.
 */
export default function AlertesSeguretat() {
  const [alertes, setAlertes] = useState<Alerta[]>([])
  const [obert, setObert] = useState(false)

  useEffect(() => {
    let viu = true
    const carrega = () => getAlertesSeguretat().then(a => { if (viu) setAlertes(a) }).catch(() => { /* sense permís o sense connexió */ })
    carrega()
    const id = setInterval(carrega, 5 * 60_000)
    return () => { viu = false; clearInterval(id) }
  }, [])

  if (alertes.length === 0) return null
  const pitjor: GravetatAlerta = alertes.some(a => a.gravetat === 'ALTA') ? 'ALTA' : alertes.some(a => a.gravetat === 'MITJANA') ? 'MITJANA' : 'INFO'

  return (
    <div className="print:hidden">
      <div role="alert" className={`${ESTIL[pitjor].barra} text-sm px-6 py-2 flex flex-wrap items-center justify-between gap-2`}>
        <span>
          {pitjor === 'INFO' ? 'ℹ' : '⚠'} <strong>Seguretat:</strong> {alertes.length} {alertes.length === 1 ? 'alerta' : 'alertes'}
          {' '}({alertes.filter(a => a.gravetat === 'ALTA').length} altes · {alertes.filter(a => a.gravetat === 'MITJANA').length} mitjanes)
        </span>
        <span className="flex items-center gap-4">
          <Link to="/admin/audit" className="underline text-white/90 hover:text-white">Veure l'auditoria</Link>
          <button type="button" onClick={() => setObert(o => !o)} aria-expanded={obert} className="underline text-white/90 hover:text-white">
            {obert ? 'Amagar' : 'Veure-les'}
          </button>
        </span>
      </div>
      {obert && (
        <ul className="bg-white border-b border-gray-200 divide-y divide-gray-100 text-sm">
          {alertes.map(a => (
            <li key={a.codi + a.titol} className="px-6 py-3 flex items-start gap-3">
              <span className={`shrink-0 text-xs px-2 py-0.5 rounded-full ${ESTIL[a.gravetat].punt}`}>{ESTIL[a.gravetat].etiqueta}</span>
              <div className="min-w-0">
                <p className="font-medium text-gray-800">{a.titol}</p>
                <p className="text-gray-600 text-xs mt-0.5 break-words">{a.detall}</p>
              </div>
              <span className="ml-auto shrink-0 text-xs text-gray-400">{fa(a.ultima)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

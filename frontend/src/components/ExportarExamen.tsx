import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { descarregaExportacio, missatgeErrorExportacio, type TipusExportacio } from '../api/exportacions'
import { nomExportacio } from '../utils/fitxers'

/** Menú «Exportar» d'un examen: notes, Excel, respostes per a una IA (anònimes), informe i lliuraments. */
export default function ExportarExamen({ examId, titol, teFitxers = false }: {
  examId: string
  titol: string
  /** L'examen té preguntes de lliurament de fitxer: s'ofereix el ZIP */
  teFitxers?: boolean
}) {
  const [obert, setObert] = useState(false)
  const [ambModel, setAmbModel] = useState(true)
  const [ambNoms, setAmbNoms] = useState(false)
  const [ocupat, setOcupat] = useState<string | null>(null)
  const [error, setError] = useState('')
  const arrel = useRef<HTMLDivElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  // El menú es pinta fora de la targeta (portal) perquè cap contenidor amb overflow no el talli
  const [pos, setPos] = useState<{ dreta: number; top?: number; bottom?: number; maxH: number } | null>(null)

  useLayoutEffect(() => {
    if (!obert) return
    const calcula = () => {
      const r = arrel.current?.getBoundingClientRect()
      if (!r) return
      const marge = 8
      const sota = window.innerHeight - r.bottom - marge
      const sobre = r.top - marge
      const amunt = sota < 360 && sobre > sota
      setPos({
        dreta: Math.max(marge, window.innerWidth - r.right),
        ...(amunt ? { bottom: window.innerHeight - r.top + 4 } : { top: r.bottom + 4 }),
        maxH: Math.max(160, (amunt ? sobre : sota) - 4),
      })
    }
    calcula()
    window.addEventListener('resize', calcula)
    window.addEventListener('scroll', calcula, true)
    return () => { window.removeEventListener('resize', calcula); window.removeEventListener('scroll', calcula, true) }
  }, [obert])

  useEffect(() => {
    if (!obert) return
    const fora = (e: MouseEvent) => { const t = e.target as Node; if (!arrel.current?.contains(t) && !menu.current?.contains(t)) setObert(false) }
    const tecla = (e: KeyboardEvent) => { if (e.key === 'Escape') setObert(false) }
    document.addEventListener('mousedown', fora)
    document.addEventListener('keydown', tecla)
    return () => { document.removeEventListener('mousedown', fora); document.removeEventListener('keydown', tecla) }
  }, [obert])

  const baixa = async (tipus: TipusExportacio, sufix: string) => {
    setOcupat(tipus)
    setError('')
    try {
      await descarregaExportacio(examId, tipus, nomExportacio(titol, sufix), { ambNoms, ambModel })
    } catch (err) {
      setError(await missatgeErrorExportacio(err))
    } finally {
      setOcupat(null)
    }
  }

  const Opcio = ({ tipus, sufix, text, detall }: { tipus: TipusExportacio; sufix: string; text: string; detall?: string }) => (
    <button type="button" role="menuitem" onClick={() => baixa(tipus, sufix)} disabled={ocupat !== null}
      className="w-full text-left px-3 py-1.5 rounded hover:bg-gray-50 disabled:opacity-50">
      <span className="block text-sm text-gray-800">{ocupat === tipus ? 'Generant…' : text}</span>
      {detall && <span className="block text-xs text-gray-400">{detall}</span>}
    </button>
  )

  return (
    <div ref={arrel} className="relative inline-block">
      <button type="button" onClick={() => setObert(o => !o)} aria-haspopup="menu" aria-expanded={obert}
        className="text-xs bg-blue-600 text-white px-3 py-1.5 rounded-lg hover:bg-blue-700">
        Exportar ▾
      </button>
      {obert && pos && createPortal(
        <div ref={menu} role="menu"
          style={{ position: 'fixed', right: pos.dreta, top: pos.top, bottom: pos.bottom, maxHeight: pos.maxH }}
          className="w-80 max-w-[calc(100vw-1rem)] overflow-y-auto bg-white border border-gray-200 rounded-xl shadow-lg z-50 p-2 space-y-1 text-left">
          <p className="px-3 pt-1 text-xs font-semibold text-gray-500 uppercase tracking-wide">Notes</p>
          <Opcio tipus="notes" sufix="notes.csv" text="Notes per alumne (CSV)" detall="Nota sobre 10, punts i punts de cada pregunta" />
          <Opcio tipus="notes-ra" sufix="notes-ra.csv" text="Notes per resultat d'aprenentatge (CSV)" />
          <Opcio tipus="excel" sufix="examen.xlsx" text="Excel (.xlsx)" detall="Full de notes, de RA i de respostes" />

          <p className="px-3 pt-2 text-xs font-semibold text-gray-500 uppercase tracking-wide border-t border-gray-100">Revisar amb una IA</p>
          <p className="px-3 text-xs text-gray-500">
            {ambNoms
              ? 'Amb noms i correus.'
              : 'Anònim: els alumnes surten com «Alumne A1B2C3». Baixa també la clau per tornar a posar-los el nom.'}
          </p>
          <Opcio tipus="respostes" sufix={ambNoms ? 'respostes.md' : 'respostes-anonimes.md'}
            text={ambNoms ? 'Respostes dels alumnes, amb noms (.md)' : 'Respostes anònimes (.md)'} />
          {!ambNoms && (
            <Opcio tipus="clau" sufix="clau-alumnes.csv" text="Clau d'alumnes (CSV)" detall="No l'enviïs a la IA: lliga cada codi amb l'alumne real" />
          )}
          <label className="flex items-start gap-2 px-3 text-xs text-gray-600 cursor-pointer">
            <input type="checkbox" checked={ambModel} onChange={e => setAmbModel(e.target.checked)} className="mt-0.5 accent-brand-600" />
            Inclou la resposta model i els criteris
          </label>
          <label className="flex items-start gap-2 px-3 text-xs text-gray-600 cursor-pointer">
            <input type="checkbox" checked={ambNoms} onChange={e => setAmbNoms(e.target.checked)} className="mt-0.5 accent-brand-600" />
            <span>Inclou noms i correus <span className="text-red-600">(no ho enviïs a un servei extern)</span></span>
          </label>
          {!ambNoms && (
            <p className="px-3 text-xs text-gray-400">Si un alumne escriu el seu nom dins d'una resposta, apareixerà al fitxer.</p>
          )}

          <p className="px-3 pt-2 text-xs font-semibold text-gray-500 uppercase tracking-wide border-t border-gray-100">Altres</p>
          <Opcio tipus="informe" sufix="informe.md" text="Informe de l'examen (.md)" detall="Estadístiques per pregunta i distribució de notes" />
          {teFitxers && <Opcio tipus="fitxers" sufix="lliuraments.zip" text="Fitxers lliurats (ZIP)" detall="Un directori per alumne" />}
          <Opcio tipus="csv-detallat" sufix="detall.csv" text="CSV detallat (una fila per resposta)" />

          {error && <p role="alert" className="mx-3 mt-1 text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1">{error}</p>}
        </div>,
        document.body
      )}
    </div>
  )
}

import { useEffect, useMemo, useState } from 'react'
import type { Exam } from '../types'
import { fixaPesosParts, treuPesosParts } from '../api/exams'

const fmt = (n: number) => String(Number(n.toFixed(2))).replace('.', ',')

/**
 * Pes (%) de cada part (secció) de l'examen sobre la nota. Es pot canviar quan l'examen ja s'ha fet (p. ex. una part
 * massa difícil): les notes es recalculen, també les que els alumnes ja veuen si estan publicades.
 */
export default function PesosParts({ exam, onCanvi }: { exam: Exam; onCanvi: (e: Exam) => void }) {
  const parts = useMemo(() => {
    const ordenades = [...exam.questions].sort((a, b) => a.ordre - b.ordre)
    const res: { id: string; titol: string; punts: number; pes: number | null }[] = []
    for (const q of ordenades) {
      if (q.tipus === 'SECTION') res.push({ id: q.id, titol: q.enunciat, punts: 0, pes: q.pesPart ?? null })
      else if (res.length > 0) res[res.length - 1].punts += q.punts
    }
    return res.filter(p => p.punts > 0 || p.pes != null)
  }, [exam])
  const total = parts.reduce((s, p) => s + p.punts, 0)
  const original = (p: { punts: number }) => total > 0 ? p.punts * 100 / total : 0
  const fixats = parts.some(p => p.pes != null)

  const [valors, setValors] = useState<Record<string, string>>({})
  const [obert, setObert] = useState(false)
  const [desant, setDesant] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setValors(Object.fromEntries(parts.map(p => [p.id, fmt(p.pes ?? original(p))])))
  }, [parts])

  // Les preguntes abans de la primera secció no tenen part: no es pot repartir.
  const primera = [...exam.questions].sort((a, b) => a.ordre - b.ordre)[0]
  if (parts.length === 0 || !primera || primera.tipus !== 'SECTION') return null

  const num = (v: string) => Number(v.replace(',', '.'))
  const suma = parts.reduce((s, p) => s + (Number.isFinite(num(valors[p.id] ?? '')) ? num(valors[p.id] ?? '') : 0), 0)
  const quadra = Math.abs(suma - 100) <= 0.01

  const desa = async () => {
    const hiHaNotesPublicades = exam.notesVisibles
    const avis = `Es recalcularan les notes de tots els alumnes d'aquest examen.${hiHaNotesPublicades
      ? '\n\nLes notes ja estan publicades: els alumnes veuran la nota canviada.' : ''}\n\nContinuar?`
    if (!confirm(avis)) return
    setDesant(true); setError('')
    try {
      onCanvi(await fixaPesosParts(exam.id, parts.map(p => ({ seccioId: p.id, pes: num(valors[p.id]) }))))
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'han pogut desar els pesos')
    } finally { setDesant(false) }
  }

  const restableix = async () => {
    if (!confirm('Tornar a la puntuació original (cada pregunta amb els seus punts)? Es recalcularan les notes.')) return
    setDesant(true); setError('')
    try { onCanvi(await treuPesosParts(exam.id)) }
    catch (e: any) { setError(e?.response?.data?.error || 'No s\'ha pogut restablir') }
    finally { setDesant(false) }
  }

  return (
    <section className="bg-white border border-gray-200 rounded-xl px-4 py-3 space-y-2">
      <button type="button" onClick={() => setObert(o => !o)} className="flex items-center gap-2 text-sm font-semibold text-gray-700 w-full text-left">
        <span>{obert ? '▾' : '▸'}</span> Pes de cada part
        {fixats && <span className="text-xs font-normal bg-amber-100 text-amber-800 px-2 py-0.5 rounded">pesos modificats</span>}
      </button>
      {obert && (
        <div className="space-y-3">
          <p className="text-xs text-gray-500">
            Reparteix la nota entre les parts (han de sumar 100 %). Els punts de les preguntes no canvien: només el que
            val cada part sobre el total. Si una part ha sortit massa difícil, en pots baixar el pes.
          </p>
          <table className="text-sm">
            <thead><tr className="text-xs text-gray-500 text-left">
              <th className="pr-4 font-medium">Part</th><th className="pr-4 font-medium text-right">Punts originals</th>
              <th className="pr-4 font-medium text-right">Pes original</th><th className="font-medium text-right">Pes nou (%)</th>
            </tr></thead>
            <tbody>
              {parts.map(p => (
                <tr key={p.id} className="border-t">
                  <td className="pr-4 py-1">{p.titol}</td>
                  <td className="pr-4 py-1 text-right tabular-nums">{fmt(p.punts)}</td>
                  <td className="pr-4 py-1 text-right tabular-nums text-gray-500">{fmt(original(p))} %</td>
                  <td className="py-1 text-right">
                    <input type="text" inputMode="decimal" value={valors[p.id] ?? ''} aria-label={`Pes de ${p.titol}`}
                      onChange={e => setValors(v => ({ ...v, [p.id]: e.target.value }))}
                      className="w-20 border rounded px-2 py-0.5 text-right tabular-nums" />
                  </td>
                </tr>
              ))}
              <tr className="border-t font-semibold">
                <td className="pr-4 py-1" colSpan={3}>Suma</td>
                <td className={`py-1 text-right tabular-nums ${quadra ? 'text-green-700' : 'text-red-600'}`}>{fmt(suma)} %</td>
              </tr>
            </tbody>
          </table>
          {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
          <div className="flex gap-2">
            <button type="button" onClick={desa} disabled={!quadra || desant}
              className="text-xs bg-brand-600 text-white px-3 py-1.5 rounded-lg hover:bg-brand-700 disabled:opacity-40">
              {desant ? 'Desant…' : 'Desa i recalcula les notes'}
            </button>
            {fixats && (
              <button type="button" onClick={restableix} disabled={desant}
                className="text-xs border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 disabled:opacity-40">
                Restableix l'original
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}

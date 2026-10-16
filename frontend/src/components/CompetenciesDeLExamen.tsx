import { useEffect, useState } from 'react'
import type { Exam } from '../types'
import { fixaCompetenciesExamen } from '../api/programacio'

interface Props {
  exam: Exam
  /** Cridada amb l'examen actualitzat després de desar. */
  onCanvi: (exam: Exam) => void
}

const separa = (text: string): string[] =>
  [...new Set(text.split(/[\s,;]+/).map(c => c.trim().toUpperCase()).filter(Boolean))]

/**
 * Competències que avalua l'examen. Els codis són lliures: si no són a la programació del mòdul no es rebutgen,
 * només surt un avís. Les preguntes trien la seva competència entre les d'aquí.
 */
export default function CompetenciesDeLExamen({ exam, onCanvi }: Props) {
  const declarades = exam.competenciesExamen ?? []
  const [text, setText] = useState(declarades.join(', '))
  const [error, setError] = useState('')
  const [desant, setDesant] = useState(false)

  useEffect(() => { setText((exam.competenciesExamen ?? []).join(', ')) }, [exam.competenciesExamen])

  const noves = separa(text)
  const canviat = noves.join('|') !== declarades.join('|')
  const avisos = exam.avisos ?? []

  const desa = async () => {
    setDesant(true)
    setError('')
    try {
      onCanvi(await fixaCompetenciesExamen(exam.id, noves))
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'han pogut desar les competències')
    } finally {
      setDesant(false)
    }
  }

  return (
    <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 text-sm space-y-2">
      <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Competències de l'examen</p>
      <p className="text-xs text-gray-500">
        Codis separats per comes (p. ex. CO1, CO3). Cada pregunta triarà la seva competència entre aquestes.
        Si en deixes la llista buida, es proposen les de les unitats triades o les del mòdul.
      </p>
      <div className="flex items-center gap-2 flex-wrap">
        <input value={text} onChange={e => setText(e.target.value)} placeholder="CO1, CO2"
          aria-label="Competències de l'examen"
          className="border rounded px-2 py-1 text-sm flex-1 min-w-[12rem] focus:outline-none focus:ring-1 focus:ring-brand-400" />
        <button onClick={desa} disabled={!canviat || desant}
          className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700 disabled:opacity-40">
          {desant ? 'Desant…' : 'Desar competències'}
        </button>
      </div>
      {avisos.length > 0 && (
        <ul className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2 space-y-1 list-disc list-inside">
          {avisos.map(a => <li key={a}>{a}</li>)}
        </ul>
      )}
      {error && <p role="alert" className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

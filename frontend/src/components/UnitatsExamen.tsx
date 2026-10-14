import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Exam, Programacio } from '../types'
import { getProgramacio, assignaUnitatsExamen } from '../api/programacio'

interface Props {
  exam: Exam
  /** Cridada amb l'examen actualitzat després de desar les UT. */
  onCanvi: (exam: Exam) => void
}

/** Unitats de treball que avalua l'examen: les preguntes només poden tenir competències d'aquestes UT. */
export default function UnitatsExamen({ exam, onCanvi }: Props) {
  const [programacio, setProgramacio] = useState<Programacio | null>(null)
  const [triades, setTriades] = useState<Set<string>>(new Set(exam.unitats.map(u => u.id)))
  const [error, setError] = useState('')
  const [desant, setDesant] = useState(false)

  useEffect(() => {
    setTriades(new Set(exam.unitats.map(u => u.id)))
  }, [exam.unitats])

  useEffect(() => {
    if (!exam.modulId) { setProgramacio(null); return }
    setError('')
    getProgramacio(exam.modulId).then(setProgramacio)
      .catch(e => setError(e?.response?.data?.error || 'No s\'ha pogut carregar la programació del mòdul'))
  }, [exam.modulId])

  if (!exam.modulId) {
    return (
      <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 text-sm text-gray-500">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1">Competències i unitats de treball</p>
        Assigna un mòdul a l'examen (des del llistat d'exàmens) per triar les unitats que avalua i les competències de cada pregunta.
      </div>
    )
  }

  const canviat = programacio !== null &&
    (triades.size !== exam.unitats.length || exam.unitats.some(u => !triades.has(u.id)))

  const alterna = (id: string) => setTriades(prev => {
    const n = new Set(prev)
    if (n.has(id)) n.delete(id); else n.add(id)
    return n
  })

  const desa = async () => {
    setDesant(true)
    setError('')
    try {
      onCanvi(await assignaUnitatsExamen(exam.id, [...triades]))
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'han pogut desar les unitats')
    } finally {
      setDesant(false)
    }
  }

  return (
    <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 text-sm space-y-2">
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
          Competències i unitats de treball · {exam.modulNom}
        </p>
        <Link to={`/professor/programacio?modul=${exam.modulId}`} className="text-xs text-brand-600 hover:underline">
          Editar la programació del mòdul
        </Link>
      </div>

      {programacio && programacio.unitats.length === 0 && (
        <p className="text-xs text-gray-500">
          El mòdul encara no té unitats ni competències. Defineix-les a la programació del mòdul; mentrestant,
          les preguntes admeten qualsevol codi de competència.
        </p>
      )}

      {programacio && programacio.unitats.length > 0 && (
        <>
          <p className="text-xs text-gray-500">
            Marca les unitats que avalua aquest examen: les preguntes només podran tenir competències d'aquestes unitats.
            Sense cap unitat marcada, es permeten totes les del mòdul.
          </p>
          <div className="space-y-1">
            {programacio.unitats.map(u => (
              <label key={u.id} className="flex items-start gap-2 cursor-pointer">
                <input type="checkbox" checked={triades.has(u.id)} onChange={() => alterna(u.id)}
                  className="accent-brand-600 w-4 h-4 mt-0.5" />
                <span className="text-gray-800">
                  <span className="font-medium">{u.codi}</span>{u.nom ? ` · ${u.nom}` : ''}
                  <span className="ml-2 inline-flex flex-wrap gap-1 align-middle">
                    {u.competencies.map(c => (
                      <span key={c} className="bg-indigo-100 text-indigo-700 text-xs px-1.5 rounded font-medium"
                        title={programacio.competencies.find(x => x.codi === c)?.descripcio || undefined}>{c}</span>
                    ))}
                  </span>
                </span>
              </label>
            ))}
          </div>
          <div className="flex items-center gap-3">
            <button onClick={desa} disabled={!canviat || desant}
              className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700 disabled:opacity-40">
              {desant ? 'Desant…' : 'Desar unitats'}
            </button>
            {exam.unitats.length > 0 && !canviat && (
              <span className="text-xs text-gray-400">{exam.unitats.map(u => u.codi).join(', ')}</span>
            )}
          </div>
        </>
      )}
      {error && <p role="alert" className="text-xs text-red-600">{error}</p>}
    </div>
  )
}

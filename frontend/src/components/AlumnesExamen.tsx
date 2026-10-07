import { useEffect, useMemo, useState } from 'react'
import { getMatricules } from '../api/matricules'
import { afegeixAlumnesExamen, getAlumnesExamen, tornaATots, treuAlumneExamen, type AlumneAcces } from '../api/audiencia'
import { publishExam } from '../api/exams'
import type { Exam, Grup, Matricula } from '../types'
import { candidatsDelModul, filtraCandidats, idsDelGrup } from '../utils/audiencia'

interface Props {
  exam: Exam
  grups: Grup[]
  /** `activar`: es publica l'examen (tots o alumnes concrets). `gestionar`: examen actiu restringit. */
  mode: 'activar' | 'gestionar'
  onClose: () => void
  onFet: () => void
}

const ESTAT: Record<AlumneAcces['estat'], { text: string; cls: string }> = {
  PENDENT:  { text: 'Pendent', cls: 'bg-gray-100 text-gray-600' },
  EN_CURS:  { text: 'Fent-lo', cls: 'bg-amber-100 text-amber-800' },
  ENTREGAT: { text: 'Entregat', cls: 'bg-green-100 text-green-700' },
}

export default function AlumnesExamen({ exam, grups, mode, onClose, onFet }: Props) {
  const [matricules, setMatricules] = useState<Matricula[]>([])
  const [concrets, setConcrets] = useState(mode === 'gestionar')
  const [triats, setTriats] = useState<Set<string>>(new Set())
  const [assignats, setAssignats] = useState<AlumneAcces[]>([])
  const [cerca, setCerca] = useState('')
  const [error, setError] = useState('')
  const [enviant, setEnviant] = useState(false)

  useEffect(() => { getMatricules().then(setMatricules).catch(() => setError('No s\'han pogut carregar els alumnes')) }, [])
  useEffect(() => {
    if (mode === 'gestionar') getAlumnesExamen(exam.id).then(l => setAssignats(l.alumnes)).catch(() => setError('No s\'ha pogut carregar la llista'))
  }, [mode, exam.id])

  const candidats = useMemo(() => candidatsDelModul(matricules, exam.modulId), [matricules, exam.modulId])
  const jaAssignats = useMemo(() => new Set(assignats.map(a => a.id)), [assignats])
  const disponibles = filtraCandidats(candidats.filter(c => !jaAssignats.has(c.id)), cerca)
  const senseModul = !exam.modulId

  const alterna = (id: string) => setTriats(t => { const n = new Set(t); n.has(id) ? n.delete(id) : n.add(id); return n })
  const afegeixGrup = (grupId: string) => {
    const g = grups.find(x => x.id === grupId)
    if (!g) return
    const ids = idsDelGrup(g.students.map(s => s.id), candidats, jaAssignats)
    if (ids.length === 0) setError(`Cap alumne del grup «${g.name}» és del mòdul d'aquest examen`)
    else { setError(''); setTriats(t => new Set([...t, ...ids])) }
  }

  const executa = async (feina: () => Promise<unknown>) => {
    setError(''); setEnviant(true)
    try { await feina() } catch (e: any) { setError(e?.response?.data?.error || e?.response?.data?.message || 'No s\'ha pogut fer') }
    finally { setEnviant(false) }
  }

  const activa = () => executa(async () => {
    if (concrets && triats.size === 0) throw { response: { data: { error: 'Tria almenys un alumne' } } }
    await publishExam(exam.id, concrets ? [...triats] : undefined)
    onFet(); onClose()
  })

  const afegeix = () => executa(async () => {
    const l = await afegeixAlumnesExamen(exam.id, [...triats])
    setAssignats(l.alumnes); setTriats(new Set()); onFet()
  })

  const esEsborrany = exam.status === 'DRAFT'

  const totsElsDelModul = () => executa(async () => {
    if (!confirm('Tornar a «Tots els del mòdul»? Es treuen els alumnes triats que encara no han començat.')) return
    await tornaATots(exam.id)
    onFet(); onClose()
  })

  const treu = (a: AlumneAcces) => executa(async () => {
    await treuAlumneExamen(exam.id, a.id)
    setAssignats(l => l.filter(x => x.id !== a.id)); onFet()
  })

  const llistaTria = (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input value={cerca} onChange={e => setCerca(e.target.value)} placeholder="Cerca per nom…"
          className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm" />
        {grups.length > 0 && (
          <select value="" onChange={e => { afegeixGrup(e.target.value); e.target.value = '' }}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm" aria-label="Afegir un grup">
            <option value="">+ Grup…</option>
            {grups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        )}
      </div>
      <div className="flex gap-3 text-xs text-brand-600">
        <button type="button" onClick={() => setTriats(t => new Set([...t, ...disponibles.map(c => c.id)]))} className="hover:underline">Tria els visibles</button>
        <button type="button" onClick={() => setTriats(new Set())} className="hover:underline">Treu la tria</button>
        <span className="ml-auto text-gray-500">{triats.size} triats</span>
      </div>
      <div className="border rounded max-h-56 overflow-y-auto divide-y">
        {disponibles.length === 0 && <p className="text-sm text-gray-400 text-center py-4">Cap alumne disponible.</p>}
        {disponibles.map(c => (
          <label key={c.id} className="flex items-center gap-2 px-3 py-1.5 text-sm cursor-pointer hover:bg-gray-50">
            <input type="checkbox" checked={triats.has(c.id)} onChange={() => alterna(c.id)} className="accent-brand-600" />
            {c.nom}
          </label>
        ))}
      </div>
    </div>
  )

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg text-gray-900 max-h-[90vh] flex flex-col">
        <div className="px-6 py-4 border-b border-gray-100">
          <h2 className="font-semibold">{mode === 'activar' ? `Activar «${exam.title}»` : `Alumnes de «${exam.title}»`}</h2>
          {mode === 'gestionar' && (
            <p className="text-xs text-gray-500 mt-0.5">
              {esEsborrany
                ? 'Si hi tries alumnes (o un grup), quan activis l\'examen només el veuran ells. Si no en tries cap, serà per a tots els del mòdul.'
                : 'L\'examen és només per a aquests alumnes. Pots afegir-ne més (p. ex. qui no l\'ha fet): el seu temps comença quan l\'obre.'}
            </p>
          )}
        </div>

        <div className="px-6 py-4 space-y-4 overflow-y-auto">
          {mode === 'activar' && (
            <div className="space-y-2 text-sm">
              <label className="flex items-start gap-2 cursor-pointer">
                <input type="radio" checked={!concrets} onChange={() => setConcrets(false)} className="mt-1 accent-brand-600" />
                <span><strong>Tots</strong> els alumnes matriculats al mòdul</span>
              </label>
              <label className={`flex items-start gap-2 ${senseModul ? 'opacity-50' : 'cursor-pointer'}`}>
                <input type="radio" checked={concrets} disabled={senseModul} onChange={() => setConcrets(true)} className="mt-1 accent-brand-600" />
                <span><strong>Alumnes concrets</strong>{senseModul && ' (primer assigna un mòdul a l\'examen)'}
                  <span className="block text-xs text-gray-500">Només ells el veuen. Després en pots afegir més, per exemple qui el fa un altre dia.</span>
                </span>
              </label>
            </div>
          )}

          {mode === 'gestionar' && (
            <div className="border rounded divide-y">
              {assignats.length === 0 && <p className="text-sm text-gray-400 text-center py-4">Cap alumne assignat.</p>}
              {assignats.map(a => (
                <div key={a.id} className="flex items-center gap-2 px-3 py-1.5 text-sm">
                  <span className="flex-1 truncate">{a.nom}</span>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${ESTAT[a.estat].cls}`}>{ESTAT[a.estat].text}</span>
                  {a.estat === 'PENDENT' && (
                    <button onClick={() => treu(a)} disabled={enviant} className="text-red-500 hover:text-red-700 text-xs" title="Treure l'accés">×</button>
                  )}
                </div>
              ))}
            </div>
          )}

          {(concrets || mode === 'gestionar') && !senseModul && (
            <div>
              {mode === 'gestionar' && <p className="text-xs font-medium text-gray-600 mb-2">Afegir alumnes</p>}
              {llistaTria}
            </div>
          )}

          {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
        </div>

        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-2">
          {mode === 'gestionar' && esEsborrany && (exam.restringit || assignats.length > 0) && (
            <button onClick={totsElsDelModul} disabled={enviant}
              className="mr-auto text-sm text-brand-600 hover:underline disabled:opacity-50">Tornar a «Tots»</button>
          )}
          <button onClick={onClose} className="border border-gray-300 rounded-lg px-4 py-2 text-sm">
            {mode === 'activar' ? 'Cancel·la' : 'Tanca'}
          </button>
          {mode === 'activar'
            ? <button onClick={activa} disabled={enviant} className="bg-green-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-green-700 disabled:opacity-50">
                {enviant ? 'Activant…' : concrets ? `Activa per a ${triats.size} alumne(s)` : 'Activa per a tots'}
              </button>
            : <button onClick={afegeix} disabled={enviant || triats.size === 0} className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700 disabled:opacity-50">
                Afegeix {triats.size > 0 ? `(${triats.size})` : ''}
              </button>}
        </div>
      </div>
    </div>
  )
}

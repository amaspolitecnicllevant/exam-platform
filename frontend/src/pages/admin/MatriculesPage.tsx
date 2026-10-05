import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getModuls } from '../../api/moduls'
import { getMatriculesByModul, enrollLot, unenroll } from '../../api/matricules'
import { getUsers } from '../../api/users'
import { useConfiguracio } from '../../context/ConfiguracioContext'
import type { Modul, Matricula, User } from '../../types'

function currentCurs(): string {
  const now = new Date(); const yr = now.getFullYear()
  const inici = now.getMonth() >= 8 ? yr : yr - 1
  return `${inici}-${String(inici + 1).slice(2)}`
}

export default function MatriculesPage() {
  const { config } = useConfiguracio()
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [students, setStudents]     = useState<User[]>([])
  const [selectedModul, setSelectedModul] = useState('')
  const [curs, setCurs]             = useState(() => config?.cursActiu || currentCurs())

  useEffect(() => {
    if (config?.cursActiu) setCurs(c => c || config.cursActiu)
  }, [config?.cursActiu])
  const [matricules, setMatricules] = useState<Matricula[]>([])
  const [loadingList, setLoadingList] = useState(false)
  const [triats, setTriats]         = useState<Set<string>>(new Set())
  const [cerca, setCerca]           = useState('')
  const [matriculant, setMatriculant] = useState(false)
  const [error, setError]           = useState('')
  const [success, setSuccess]       = useState('')

  useEffect(() => {
    Promise.all([getModuls(), getUsers()])
      .then(([m, u]) => { setModuls(m); setStudents(u.filter(u => u.role === 'STUDENT')) })
      .catch(() => setError('Error carregant dades inicials'))
  }, [])

  const loadMatricules = async () => {
    if (!selectedModul || !curs) return
    setLoadingList(true); setError('')
    try {
      const m = await getMatriculesByModul(selectedModul, curs)
      setMatricules(m)
    } catch { setError('Error carregant matrícules') }
    finally { setLoadingList(false) }
  }

  useEffect(() => { if (selectedModul && curs) loadMatricules() }, [selectedModul, curs])

  const enrolledIds = new Set(matricules.map(m => m.alumneId))
  const notEnrolled = students.filter(s => !enrolledIds.has(s.id))

  // En canviar de mòdul o curs, la selecció ja no val
  useEffect(() => { setTriats(new Set()) }, [selectedModul, curs])

  const text = cerca.trim().toLowerCase()
  const visibles = notEnrolled.filter(s => !text || s.name.toLowerCase().includes(text) || s.email.toLowerCase().includes(text))
  const totsVisiblesTriats = visibles.length > 0 && visibles.every(s => triats.has(s.id))

  const commuta = (id: string) => setTriats(prev => {
    const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const commutaTots = () => setTriats(prev => {
    const n = new Set(prev)
    visibles.forEach(s => totsVisiblesTriats ? n.delete(s.id) : n.add(s.id))
    return n
  })

  const handleEnroll = async () => {
    if (triats.size === 0 || !selectedModul || !curs) return
    setError(''); setSuccess(''); setMatriculant(true)
    try {
      const r = await enrollLot([...triats], selectedModul, curs)
      setMatricules(prev => [...prev, ...r.matriculades])
      setTriats(new Set())
      setSuccess(`${r.matriculades.length} ${r.matriculades.length === 1 ? 'alumne matriculat' : 'alumnes matriculats'}`
        + (r.jaMatriculats > 0 ? ` (${r.jaMatriculats} ja ho estaven)` : '') + '.')
    } catch (e: any) {
      setError(e?.response?.data?.error || 'Error matriculant')
    } finally { setMatriculant(false) }
  }

  const handleUnenroll = async (m: Matricula) => {
    const alumne = students.find(s => s.id === m.alumneId)
    if (!confirm(`Desmatricular "${alumne?.name ?? m.alumneId}" del mòdul?`)) return
    setError('')
    try {
      await unenroll(m.id)
      setMatricules(prev => prev.filter(x => x.id !== m.id))
    } catch (e) { setError(e instanceof Error ? e.message : 'Error desmatriculant') }
  }

  const modulName = moduls.find(m => m.id === selectedModul)

  return (
    <Layout>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-brand-700">Gestió de matrícules</h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2 text-sm flex justify-between">
            <span>{error}</span>
            <button onClick={() => setError('')} className="font-bold">×</button>
          </div>
        )}
        {success && (
          <div className="bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-2 text-sm flex justify-between">
            <span>{success}</span>
            <button onClick={() => setSuccess('')} className="font-bold">×</button>
          </div>
        )}

        {/* Selector */}
        <div className="bg-white border rounded-xl p-5 flex flex-wrap gap-4 items-end">
          <div className="flex flex-col gap-1 flex-1 min-w-48">
            <label className="text-xs font-medium text-gray-600">Mòdul (assignatura)</label>
            <select value={selectedModul} onChange={e => setSelectedModul(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500">
              <option value="">Tria un mòdul…</option>
              {moduls.map(m => (
                <option key={m.id} value={m.id}>{m.codi} — {m.nom} ({m.cicleNom})</option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-gray-600">Curs acadèmic</label>
            <input type="text" value={curs} onChange={e => setCurs(e.target.value)}
              placeholder="p. ex. 2026-27"
              className="border rounded-lg px-3 py-2 text-sm w-32 focus:outline-none focus:ring-2 focus:ring-brand-500" />
          </div>
        </div>

        {selectedModul && curs && (
          <>
            {/* Afegir matrícula */}
            <div className="bg-white border rounded-xl p-5 space-y-3">
              <h2 className="font-medium text-gray-800 text-sm">
                Matricular alumnes a <span className="font-mono text-indigo-700">{modulName?.codi}</span> {modulName?.nom} — curs {curs}
              </h2>
              {notEnrolled.length > 0 && (
                <>
                  <div className="flex flex-wrap items-center gap-3">
                    <input type="search" value={cerca} onChange={e => setCerca(e.target.value)}
                      placeholder="Cerca per nom o correu…" aria-label="Cerca alumnes"
                      className="flex-1 min-w-[12rem] border rounded-lg px-3 py-1.5 text-sm" />
                    <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                      <input type="checkbox" checked={totsVisiblesTriats} onChange={commutaTots}
                        disabled={visibles.length === 0} className="accent-brand-600 w-4 h-4" />
                      {text ? `Tots els trobats (${visibles.length})` : `Tots (${visibles.length})`}
                    </label>
                  </div>
                  <ul className="border rounded-lg divide-y max-h-72 overflow-auto">
                    {visibles.map(s => (
                      <li key={s.id}>
                        <label className="flex items-center gap-3 px-3 py-1.5 text-sm cursor-pointer hover:bg-gray-50">
                          <input type="checkbox" checked={triats.has(s.id)} onChange={() => commuta(s.id)}
                            className="accent-brand-600 w-4 h-4" />
                          <span className="text-gray-800">{s.name}</span>
                          <span className="text-xs text-gray-400">{s.email}</span>
                        </label>
                      </li>
                    ))}
                    {visibles.length === 0 && <li className="px-3 py-3 text-xs text-gray-400">Cap alumne coincideix amb la cerca.</li>}
                  </ul>
                  <button onClick={handleEnroll} disabled={triats.size === 0 || matriculant}
                    className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-brand-700 disabled:opacity-50">
                    {matriculant ? 'Matriculant…' : `Matricular ${triats.size} ${triats.size === 1 ? 'alumne' : 'alumnes'}`}
                  </button>
                </>
              )}
              {notEnrolled.length === 0 && (
                <p className="text-xs text-gray-400">Tots els alumnes ja estan matriculats en aquest mòdul i curs.</p>
              )}
            </div>

            {/* Llista matriculats */}
            <div className="bg-white border rounded-xl overflow-hidden">
              <div className="bg-gray-50 border-b px-4 py-3 flex items-center justify-between">
                <h2 className="font-medium text-gray-800 text-sm">
                  Alumnes matriculats
                  {!loadingList && (
                    <span className="ml-2 text-xs text-gray-500">({matricules.length})</span>
                  )}
                </h2>
                {loadingList && <span className="text-xs text-gray-400">Carregant…</span>}
              </div>
              <table className="w-full text-sm">
                <thead className="border-b">
                  <tr>
                    {['Alumne', 'Email', ''].map(h => (
                      <th key={h} className="px-4 py-3 text-left font-medium text-gray-600">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {matricules.length === 0 && !loadingList && (
                    <tr>
                      <td colSpan={3} className="px-4 py-8 text-center text-gray-400 text-xs">
                        Cap alumne matriculat en aquest mòdul i curs.
                      </td>
                    </tr>
                  )}
                  {matricules.map(m => {
                    const s = students.find(s => s.id === m.alumneId)
                    return (
                      <tr key={m.id} className="hover:bg-gray-50">
                        <td className="px-4 py-2 font-medium text-gray-800">{s?.name ?? m.alumneNom}</td>
                        <td className="px-4 py-2 text-gray-500 text-xs">{s?.email ?? '—'}</td>
                        <td className="px-4 py-2 text-right">
                          <button onClick={() => handleUnenroll(m)}
                            className="text-xs border border-red-200 rounded px-2 py-1 hover:bg-red-50 text-red-600">
                            Desmatricular
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}

        {!selectedModul && (
          <p className="text-sm text-gray-400 text-center py-8">
            Selecciona un mòdul per veure i gestionar les matrícules.
          </p>
        )}
      </div>
    </Layout>
  )
}

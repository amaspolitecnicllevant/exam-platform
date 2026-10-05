import { useState, useEffect } from 'react'
import Layout from '../../components/Layout'
import { getGrups, createGrup, setGrupStudents, deleteGrup, assignGrupModul } from '../../api/grups'
import { getUsers } from '../../api/users'
import { getModuls } from '../../api/moduls'
import type { Grup, User, Modul } from '../../types'

type Tab = 'grups' | 'alumnes'

export default function GrupsPage() {
  const [tab, setTab]               = useState<Tab>('grups')
  const [grups, setGrups]           = useState<Grup[]>([])
  const [allStudents, setStudents]  = useState<User[]>([])
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState('')
  const [saving, setSaving]         = useState(false)
  const [assigningModulGrup, setAssigningModulGrup] = useState<Grup | null>(null)
  const [selectedModulGrup, setSelectedModulGrup]   = useState('')

  // Tab Grups — crear grup
  const [newName, setNewName]       = useState('')
  const [creating, setCreating]     = useState(false)

  // Modal Grups — editar alumnes d'un grup
  const [editingGrup, setEditingGrup]         = useState<Grup | null>(null)
  const [selectedStudents, setSelectedStudents] = useState<Set<string>>(new Set())

  // Modal Alumnes — editar grups d'un alumne
  const [editingStudent, setEditingStudent]   = useState<User | null>(null)
  const [selectedGrups, setSelectedGrups]     = useState<Set<string>>(new Set())

  useEffect(() => {
    Promise.all([getGrups(), getUsers(), getModuls()])
      .then(([g, u, m]) => {
        setGrups(g)
        setStudents(u.filter(u => u.role === 'STUDENT'))
        setModuls(m)
      })
      .catch(() => setError('Error carregant dades'))
      .finally(() => setLoading(false))
  }, [])

  // ── Helpers ──────────────────────────────────────────────────────────────

  const grupIdsForStudent = (studentId: string): Set<string> =>
    new Set(grups.filter(g => g.students.some(s => s.id === studentId)).map(g => g.id))

  // ── Accions Grups ─────────────────────────────────────────────────────────

  const handleCreateGrup = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newName.trim()) return
    setCreating(true)
    try {
      const g = await createGrup(newName.trim())
      setGrups(prev => [...prev, g].sort((a, b) => a.name.localeCompare(b.name)))
      setNewName('')
    } catch { setError('Error creant el grup') }
    finally { setCreating(false) }
  }

  const openGrupEdit = (g: Grup) => {
    setEditingGrup(g)
    setSelectedStudents(new Set(g.students.map(s => s.id)))
  }

  const handleSaveGrupStudents = async () => {
    if (!editingGrup) return
    setSaving(true)
    try {
      const updated = await setGrupStudents(editingGrup.id, [...selectedStudents])
      setGrups(prev => prev.map(g => g.id === updated.id ? updated : g))
      setEditingGrup(null)
    } catch { setError('Error desant els alumnes') }
    finally { setSaving(false) }
  }

  const handleDeleteGrup = async (g: Grup) => {
    if (!confirm(`Eliminar el grup "${g.name}"?`)) return
    try {
      await deleteGrup(g.id)
      setGrups(prev => prev.filter(x => x.id !== g.id))
    } catch { setError('Error eliminant el grup') }
  }

  const handleAssignModulGrup = async () => {
    if (!assigningModulGrup || !selectedModulGrup) return
    try {
      const updated = await assignGrupModul(assigningModulGrup.id, selectedModulGrup)
      setGrups(prev => prev.map(g => g.id === updated.id ? updated : g))
      setAssigningModulGrup(null)
    } catch { setError('Error assignant el mòdul al grup') }
  }

  // ── Accions Alumnes ───────────────────────────────────────────────────────

  const openStudentEdit = (student: User) => {
    setEditingStudent(student)
    setSelectedGrups(grupIdsForStudent(student.id))
  }

  const handleSaveStudentGrups = async () => {
    if (!editingStudent) return
    setSaving(true)
    try {
      const oldIds = grupIdsForStudent(editingStudent.id)
      const toAdd    = [...selectedGrups].filter(id => !oldIds.has(id))
      const toRemove = [...oldIds].filter(id => !selectedGrups.has(id))

      let updatedGrups = [...grups]

      for (const gId of toAdd) {
        const g = updatedGrups.find(x => x.id === gId)!
        const studentIds = [...g.students.map(s => s.id), editingStudent.id]
        const updated = await setGrupStudents(gId, studentIds)
        updatedGrups = updatedGrups.map(x => x.id === gId ? updated : x)
      }
      for (const gId of toRemove) {
        const g = updatedGrups.find(x => x.id === gId)!
        const studentIds = g.students.map(s => s.id).filter(id => id !== editingStudent.id)
        const updated = await setGrupStudents(gId, studentIds)
        updatedGrups = updatedGrups.map(x => x.id === gId ? updated : x)
      }

      setGrups(updatedGrups)
      setEditingStudent(null)
    } catch { setError('Error desant els grups') }
    finally { setSaving(false) }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) return <Layout><p className="text-gray-400 p-8">Carregant…</p></Layout>

  return (
    <Layout>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-brand-700">Grups i alumnes</h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2 text-sm">
            {error}
          </div>
        )}

        {/* Tabs */}
        <div className="flex border-b border-gray-200">
          {(['grups', 'alumnes'] as Tab[]).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={`px-5 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors capitalize ${
                tab === t
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>
              {t === 'grups' ? `Grups (${grups.length})` : `Alumnes (${allStudents.length})`}
            </button>
          ))}
        </div>

        {/* ── Tab: Grups ── */}
        {tab === 'grups' && (
          <div className="space-y-6">
            <form onSubmit={handleCreateGrup} className="flex gap-2">
              <input type="text" value={newName} onChange={e => setNewName(e.target.value)}
                placeholder="Nom del grup (p. ex. 1r SMX)"
                className="flex-1 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <button type="submit" disabled={creating || !newName.trim()}
                className="bg-brand-600 hover:bg-brand-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50">
                {creating ? 'Creant…' : 'Crear grup'}
              </button>
            </form>

            {grups.length === 0
              ? <p className="text-gray-400 text-sm">Encara no hi ha grups.</p>
              : (
                <div className="space-y-3">
                  {grups.map(g => (
                    <div key={g.id}
                      className="bg-white border border-gray-200 rounded-xl px-5 py-4 flex items-center justify-between shadow-sm">
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="font-semibold text-gray-800">{g.name}</p>
                          {g.modulNom && (
                            <span className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full font-mono">
                              {g.modulNom}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-500 mt-0.5">
                          {g.students.length === 0
                            ? 'Sense alumnes'
                            : `${g.students.length} alumne${g.students.length !== 1 ? 's' : ''}: ${g.students.slice(0, 4).map(s => s.name).join(', ')}${g.students.length > 4 ? '…' : ''}`}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        <button onClick={() => { setAssigningModulGrup(g); setSelectedModulGrup(g.modulId ?? '') }}
                          className="text-sm border border-indigo-200 rounded-lg px-3 py-1.5 hover:bg-indigo-50 text-indigo-600">
                          Mòdul
                        </button>
                        <button onClick={() => openGrupEdit(g)}
                          className="text-sm border border-gray-300 rounded-lg px-3 py-1.5 hover:bg-gray-50 text-gray-700">
                          Alumnes
                        </button>
                        <button onClick={() => handleDeleteGrup(g)}
                          className="text-sm border border-red-200 rounded-lg px-3 py-1.5 hover:bg-red-50 text-red-600">
                          Eliminar
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
          </div>
        )}

        {/* ── Tab: Alumnes ── */}
        {tab === 'alumnes' && (
          <div>
            {allStudents.length === 0
              ? (
                <p className="text-gray-400 text-sm">
                  Encara no hi ha alumnes. Crea'ls a «Gestió d'usuaris».
                </p>
              ) : (
                <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Alumne</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Correu</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Grups</th>
                        <th className="px-4 py-3 w-24"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {allStudents.map(s => {
                        const myGrups = grups.filter(g => g.students.some(x => x.id === s.id))
                        return (
                          <tr key={s.id} className="hover:bg-gray-50">
                            <td className="px-4 py-3 font-medium text-gray-800">{s.name}</td>
                            <td className="px-4 py-3 text-gray-500">{s.email}</td>
                            <td className="px-4 py-3">
                              {myGrups.length === 0
                                ? <span className="text-gray-300 text-xs italic">Sense grup</span>
                                : (
                                  <div className="flex flex-wrap gap-1">
                                    {myGrups.map(g => (
                                      <span key={g.id}
                                        className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded-full font-medium">
                                        {g.name}
                                      </span>
                                    ))}
                                  </div>
                                )}
                            </td>
                            <td className="px-4 py-3 text-right">
                              <button onClick={() => openStudentEdit(s)}
                                className="text-xs border border-gray-300 rounded-lg px-3 py-1.5 hover:bg-gray-100 text-gray-700">
                                Assignar
                              </button>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
          </div>
        )}
      </div>

      {/* Modal: assignar mòdul al grup */}
      {assigningModulGrup && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Mòdul de «{assigningModulGrup.name}»</h2>
              <p className="text-xs text-gray-500 mt-0.5">Relaciona el grup amb un mòdul professional.</p>
            </div>
            <div className="px-6 py-4">
              <select value={selectedModulGrup} onChange={e => setSelectedModulGrup(e.target.value)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-400">
                <option value="">— Sense mòdul —</option>
                {moduls.map(m => (
                  <option key={m.id} value={m.id}>{m.codi} — {m.nom} ({m.cicleNom})</option>
                ))}
              </select>
            </div>
            <div className="px-6 py-4 border-t border-gray-100 flex justify-between">
              <button onClick={() => setAssigningModulGrup(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
              <button onClick={handleAssignModulGrup} disabled={!selectedModulGrup}
                className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700 disabled:opacity-50">
                Assignar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: alumnes d'un grup */}
      {editingGrup && (
        <Modal title={`Alumnes de «${editingGrup.name}»`}
          subtitle={`${selectedStudents.size} seleccionat${selectedStudents.size !== 1 ? 's' : ''}`}
          onClose={() => setEditingGrup(null)}
          onSave={handleSaveGrupStudents} saving={saving}>
          {allStudents.length === 0
            ? <p className="text-sm text-gray-400 text-center py-4">No hi ha alumnes al sistema.</p>
            : allStudents.map(s => (
              <CheckRow key={s.id}
                label={s.name} sub={s.email}
                checked={selectedStudents.has(s.id)}
                onChange={() => setSelectedStudents(prev => toggle(prev, s.id))} />
            ))}
        </Modal>
      )}

      {/* Modal: grups d'un alumne */}
      {editingStudent && (
        <Modal title={`Grups de ${editingStudent.name}`}
          subtitle={`${selectedGrups.size} grup${selectedGrups.size !== 1 ? 's' : ''} seleccionat${selectedGrups.size !== 1 ? 's' : ''}`}
          onClose={() => setEditingStudent(null)}
          onSave={handleSaveStudentGrups} saving={saving}>
          {grups.length === 0
            ? <p className="text-sm text-gray-400 text-center py-4">No hi ha grups. Crea'n un primer.</p>
            : grups.map(g => (
              <CheckRow key={g.id}
                label={g.name}
                sub={`${g.students.length} alumne${g.students.length !== 1 ? 's' : ''}`}
                checked={selectedGrups.has(g.id)}
                onChange={() => setSelectedGrups(prev => toggle(prev, g.id))} />
            ))}
        </Modal>
      )}
    </Layout>
  )
}

// ── Helpers de UI ───────────────────────────────────────────────────────────

function toggle(set: Set<string>, id: string): Set<string> {
  const next = new Set(set)
  next.has(id) ? next.delete(id) : next.add(id)
  return next
}

function Modal({ title, subtitle, onClose, onSave, saving, children }: {
  title: string; subtitle: string
  onClose: () => void; onSave: () => void; saving: boolean
  children: React.ReactNode
}) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md max-h-[80vh] flex flex-col">
        <div className="px-6 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-800">{title}</h2>
          <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>
        </div>
        <div className="overflow-y-auto flex-1 px-4 py-3 space-y-1">{children}</div>
        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-2">
          <button onClick={onClose}
            className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
            Cancel·lar
          </button>
          <button onClick={onSave} disabled={saving}
            className="bg-brand-600 hover:bg-brand-700 text-white rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50">
            {saving ? 'Desant…' : 'Desar'}
          </button>
        </div>
      </div>
    </div>
  )
}

function CheckRow({ label, sub, checked, onChange }: {
  label: string; sub: string; checked: boolean; onChange: () => void
}) {
  return (
    <label className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-gray-50 cursor-pointer">
      <input type="checkbox" checked={checked} onChange={onChange}
        className="accent-brand-600 w-4 h-4 flex-shrink-0" />
      <span className="flex-1 text-sm text-gray-800">{label}</span>
      <span className="text-xs text-gray-400">{sub}</span>
    </label>
  )
}

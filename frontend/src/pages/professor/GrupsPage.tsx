import { useState, useEffect } from 'react'
import Layout from '../../components/Layout'
import { getGrups, createGrup, setGrupStudents, deleteGrup, assignGrupModul } from '../../api/grups'
import { getUsers } from '../../api/users'
import { getModuls } from '../../api/moduls'
import { getMatricules } from '../../api/matricules'
import {
  FILTRE_BUIT, filtraAlumnes, filtraPerMatricula, filtreInicial,
  cursosDisponibles, modulsAmbMatricula, ciclesAmbMatricula,
  filtraPerGrup, filtraGrups, GRUP_CAP,
  type FiltreAlumnes,
} from '../../utils/filtreAlumnes'
import type { Grup, User, Modul, Matricula } from '../../types'

type Tab = 'grups' | 'alumnes'

export default function GrupsPage() {
  const [tab, setTab]               = useState<Tab>('grups')
  const [grups, setGrups]           = useState<Grup[]>([])
  const [allStudents, setStudents]  = useState<User[]>([])
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [matricules, setMatricules] = useState<Matricula[]>([])
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
  const [filtre, setFiltre]                   = useState<FiltreAlumnes>(FILTRE_BUIT)

  // Modal Alumnes — editar grups d'un alumne
  const [editingStudent, setEditingStudent]   = useState<User | null>(null)
  const [selectedGrups, setSelectedGrups]     = useState<Set<string>>(new Set())
  const [cercaGrups, setCercaGrups]           = useState('')

  // Pestanya Alumnes — filtres de la llista
  const [filtreTab, setFiltreTab]             = useState<FiltreAlumnes>(FILTRE_BUIT)
  const [filtreGrupTab, setFiltreGrupTab]     = useState('')

  useEffect(() => {
    // Si les matrícules no es poden carregar, la llista d'alumnes continua funcionant (sense filtres de matrícula)
    Promise.all([getGrups(), getUsers(), getModuls(), getMatricules().catch(() => [] as Matricula[])])
      .then(([g, u, m, mat]) => {
        setGrups(g)
        setStudents(u.filter(u => u.role === 'STUDENT'))
        setModuls(m)
        setMatricules(mat)
      })
      .catch(() => setError('Error carregant dades'))
      .finally(() => setLoading(false))
  }, [])

  // ── Helpers ──────────────────────────────────────────────────────────────

  const grupIdsForStudent = (studentId: string): Set<string> =>
    new Set(grups.filter(g => g.students.some(s => s.id === studentId)).map(g => g.id))

  // Llista d'alumnes del modal de grup, segons els filtres
  const alumnesVisibles = editingGrup
    ? filtraAlumnes(allStudents, matricules, moduls, filtre, selectedStudents)
    : []
  const ciclesFiltre  = ciclesAmbMatricula(moduls, matricules)
  const modulsFiltre  = modulsAmbMatricula(moduls, matricules, filtre.cicleId)
  const cursosFiltre  = cursosDisponibles(matricules)
  const idsMatriculats = new Set(matricules.map(m => m.alumneId))
  const senseMatricula = allStudents.filter(a => !idsMatriculats.has(a.id)).length
  const alumnesTab = filtraPerGrup(
    filtraAlumnes(allStudents, matricules, moduls, filtreTab, new Set()), grups, filtreGrupTab)
  const modulsFiltreTab = modulsAmbMatricula(moduls, matricules, filtreTab.cicleId)
  const filtreTabActiu = filtreTab.text !== '' || filtraPerMatricula(filtreTab) || filtreGrupTab !== ''
  const grupsVisiblesModal = filtraGrups(grups, cercaGrups)
  const filtreActiu = filtre.text !== '' || filtre.nomesSeleccionats || filtraPerMatricula(filtre)
  const seleccionatsFora = [...selectedStudents].filter(id => !alumnesVisibles.some(a => a.id === id)).length

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
    setFiltre(filtreInicial(g.modulId, matricules, moduls))
  }

  const canviaFiltre = (canvis: Partial<FiltreAlumnes>) => setFiltre(prev => ({ ...prev, ...canvis }))

  /** Marca o desmarca tots els alumnes que ara es veuen (els que queden fora del filtre no es toquen). */
  const marcaVisibles = (visibles: User[], marcar: boolean) =>
    setSelectedStudents(prev => {
      const next = new Set(prev)
      visibles.forEach(a => (marcar ? next.add(a.id) : next.delete(a.id)))
      return next
    })

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
    setCercaGrups('')
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
                <>
                <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-3 mb-3 space-y-2">
                  <div className="flex flex-wrap gap-2">
                    <input type="search" value={filtreTab.text}
                      onChange={e => setFiltreTab(prev => ({ ...prev, text: e.target.value }))}
                      placeholder="Cerca per nom o correu…"
                      className="flex-1 min-w-[12rem] border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                    {matricules.length > 0 && (<>
                      <select value={filtreTab.cicleId} aria-label="Cicle"
                        onChange={e => setFiltreTab(prev => ({ ...prev, cicleId: e.target.value, modulId: '' }))}
                        className="border border-gray-300 rounded-lg px-2 py-2 text-sm">
                        <option value="">Tots els cicles</option>
                        {ciclesFiltre.map(c => <option key={c.id} value={c.id}>{c.nom}</option>)}
                      </select>
                      <select value={filtreTab.modulId} aria-label="Mòdul"
                        onChange={e => setFiltreTab(prev => ({ ...prev, modulId: e.target.value }))}
                        className="border border-gray-300 rounded-lg px-2 py-2 text-sm">
                        <option value="">Tots els mòduls</option>
                        {modulsFiltreTab.map(m => <option key={m.id} value={m.id}>{m.codi} — {m.nom}</option>)}
                      </select>
                      <select value={filtreTab.curs} aria-label="Curs"
                        onChange={e => setFiltreTab(prev => ({ ...prev, curs: e.target.value }))}
                        className="border border-gray-300 rounded-lg px-2 py-2 text-sm">
                        <option value="">Tots els cursos</option>
                        {cursosFiltre.map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </>)}
                    <select value={filtreGrupTab} aria-label="Grup"
                      onChange={e => setFiltreGrupTab(e.target.value)}
                      className="border border-gray-300 rounded-lg px-2 py-2 text-sm">
                      <option value="">Tots els grups</option>
                      <option value={GRUP_CAP}>Sense grup</option>
                      {grups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                    </select>
                  </div>
                  <div className="flex items-center justify-between text-xs text-gray-500">
                    <span>Es mostren {alumnesTab.length} de {allStudents.length} alumnes</span>
                    {filtreTabActiu && (
                      <button type="button"
                        onClick={() => { setFiltreTab(FILTRE_BUIT); setFiltreGrupTab('') }}
                        className="hover:underline">Treure filtres</button>
                    )}
                  </div>
                  {filtraPerMatricula(filtreTab) && senseMatricula > 0 && (
                    <p className="text-xs text-amber-700">
                      {senseMatricula} alumne{senseMatricula !== 1 ? 's' : ''} sense matrícula no surt{senseMatricula !== 1 ? 'en' : ''} amb
                      el filtre de cicle, mòdul o curs.
                    </p>
                  )}
                </div>
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
                      {alumnesTab.length === 0 && (
                        <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">
                          Cap alumne coincideix amb els filtres.
                        </td></tr>
                      )}
                      {alumnesTab.map(s => {
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
                </>
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
          subtitle={`${selectedStudents.size} seleccionat${selectedStudents.size !== 1 ? 's' : ''}`
            + (seleccionatsFora > 0 ? ` (${seleccionatsFora} fora del filtre actual)` : '')}
          onClose={() => setEditingGrup(null)}
          onSave={handleSaveGrupStudents} saving={saving}>
          {allStudents.length === 0
            ? <p className="text-sm text-gray-400 text-center py-4">No hi ha alumnes al sistema.</p>
            : <>
              <div className="sticky top-0 bg-white pb-2 space-y-2 border-b border-gray-100 mb-1">
                <input type="search" value={filtre.text}
                  onChange={e => canviaFiltre({ text: e.target.value })}
                  placeholder="Cerca per nom o correu…"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                {matricules.length > 0 && (
                  <div className="grid grid-cols-2 gap-2">
                    <select value={filtre.cicleId} aria-label="Cicle"
                      onChange={e => canviaFiltre({ cicleId: e.target.value, modulId: '' })}
                      className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm col-span-2">
                      <option value="">Tots els cicles</option>
                      {ciclesFiltre.map(c => <option key={c.id} value={c.id}>{c.nom}</option>)}
                    </select>
                    <select value={filtre.modulId} aria-label="Mòdul"
                      onChange={e => canviaFiltre({ modulId: e.target.value })}
                      className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm">
                      <option value="">Tots els mòduls</option>
                      {modulsFiltre.map(m => <option key={m.id} value={m.id}>{m.codi} — {m.nom}</option>)}
                    </select>
                    <select value={filtre.curs} aria-label="Curs"
                      onChange={e => canviaFiltre({ curs: e.target.value })}
                      className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm">
                      <option value="">Tots els cursos</option>
                      {cursosFiltre.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                  </div>
                )}
                <label className="flex items-center gap-2 text-xs text-gray-600 cursor-pointer">
                  <input type="checkbox" checked={filtre.nomesSeleccionats}
                    onChange={e => canviaFiltre({ nomesSeleccionats: e.target.checked })}
                    className="accent-brand-600" />
                  Mostra només els seleccionats
                </label>
                <div className="flex items-center justify-between gap-2 text-xs">
                  <span className="text-gray-500">
                    Es mostren {alumnesVisibles.length} de {allStudents.length}
                  </span>
                  <span className="flex gap-3">
                    {filtreActiu && (
                      <button type="button" onClick={() => setFiltre(FILTRE_BUIT)}
                        className="text-gray-500 hover:underline">Treure filtres</button>
                    )}
                    <button type="button" disabled={alumnesVisibles.length === 0}
                      onClick={() => marcaVisibles(alumnesVisibles, true)}
                      className="text-brand-600 hover:underline disabled:opacity-40">
                      Marcar els {alumnesVisibles.length} visibles
                    </button>
                    <button type="button" disabled={alumnesVisibles.length === 0}
                      onClick={() => marcaVisibles(alumnesVisibles, false)}
                      className="text-gray-500 hover:underline disabled:opacity-40">Desmarcar-los</button>
                  </span>
                </div>
                {filtraPerMatricula(filtre) && senseMatricula > 0 && (
                  <p className="text-xs text-amber-700">
                    {senseMatricula} alumne{senseMatricula !== 1 ? 's' : ''} sense matrícula no surt{senseMatricula !== 1 ? 'en' : ''} amb
                    aquest filtre. Treu els filtres per veure'ls.
                  </p>
                )}
              </div>
              {alumnesVisibles.length === 0
                ? <p className="text-sm text-gray-400 text-center py-4">Cap alumne coincideix amb els filtres.</p>
                : alumnesVisibles.map(s => (
                  <CheckRow key={s.id}
                    label={s.name} sub={s.email}
                    checked={selectedStudents.has(s.id)}
                    onChange={() => setSelectedStudents(prev => toggle(prev, s.id))} />
                ))}
            </>}
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
            : <>
              <div className="sticky top-0 bg-white pb-2 border-b border-gray-100 mb-1">
                <input type="search" value={cercaGrups}
                  onChange={e => setCercaGrups(e.target.value)}
                  placeholder="Cerca un grup pel nom o el mòdul…"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
              </div>
              {grupsVisiblesModal.length === 0
                ? <p className="text-sm text-gray-400 text-center py-4">Cap grup coincideix.</p>
                : grupsVisiblesModal.map(g => (
                  <CheckRow key={g.id}
                    label={g.name}
                    sub={`${g.modulNom ? g.modulNom + ' · ' : ''}${g.students.length} alumne${g.students.length !== 1 ? 's' : ''}`}
                    checked={selectedGrups.has(g.id)}
                    onChange={() => setSelectedGrups(prev => toggle(prev, g.id))} />
                ))}
            </>}
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

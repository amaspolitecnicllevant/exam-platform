import { useState, useEffect } from 'react'
import Layout from '../../components/Layout'
import { getGrups, createGrup, setGrupStudents, deleteGrup, assignGrupModul } from '../../api/grups'
import { getUsers, deleteUsers } from '../../api/users'
import { useAuth } from '../../context/AuthContext'
import { getModuls } from '../../api/moduls'
import { getMatricules } from '../../api/matricules'
import {
  FILTRE_BUIT, filtraAlumnes, filtraPerMatricula, filtreInicial,
  cursosDisponibles, modulsAmbMatricula, ciclesAmbMatricula,
  filtraPerGrup, filtraGrups, GRUP_CAP,
  type FiltreAlumnes,
} from '../../utils/filtreAlumnes'
import { SortTh, useOrdre, ordena } from '../../components/SortTh'
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

  // Ordenació i selecció a les taules de les pestanyes
  const ordreGrups   = useOrdre<'nom' | 'modul' | 'alumnes'>('nom')
  const ordreAlumnes = useOrdre<'nom' | 'correu' | 'grups'>('nom')
  const [grupsTriats, setGrupsTriats]     = useState<Set<string>>(new Set())
  const [alumnesTriats, setAlumnesTriats] = useState<Set<string>>(new Set())
  const [grupDestiLot, setGrupDestiLot]   = useState('')
  const { user: yo }                      = useAuth()
  const esAdmin                           = yo?.role === 'ADMIN'
  const [forcaEliminar, setForcaEliminar] = useState(false)

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
  const nomsGrups = (id: string) =>
    grups.filter(g => g.students.some(x => x.id === id)).map(g => g.name).join(', ')
  const alumnesOrdenats = ordena(alumnesTab, ordreAlumnes.ordre, (a, k) =>
    k === 'nom' ? a.name : k === 'correu' ? a.email : nomsGrups(a.id))
  const grupsOrdenats = ordena(grups, ordreGrups.ordre, (g, k) =>
    k === 'nom' ? g.name : k === 'modul' ? (g.modulNom ?? '') : g.students.length)
  const tots = (llista: { id: string }[], triats: Set<string>) =>
    llista.length > 0 && llista.every(x => triats.has(x.id))
  const commutaTots = (llista: { id: string }[], set: React.Dispatch<React.SetStateAction<Set<string>>>) =>
    set(prev => {
      const n = new Set(prev)
      const marcats = llista.every(x => n.has(x.id))
      llista.forEach(x => (marcats ? n.delete(x.id) : n.add(x.id)))
      return n
    })
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

  const handleDeleteGrupsTriats = async () => {
    const ids = [...grupsTriats]
    if (!ids.length || !confirm(`Eliminar ${ids.length} grup${ids.length > 1 ? 's' : ''}?`)) return
    setSaving(true); setError('')
    const fets = new Set<string>()
    for (const id of ids) {
      try { await deleteGrup(id); fets.add(id) } catch { /* es comunica a baix */ }
    }
    setGrups(prev => prev.filter(g => !fets.has(g.id)))
    setGrupsTriats(new Set(ids.filter(id => !fets.has(id))))
    if (fets.size < ids.length) setError(`No s'han pogut eliminar ${ids.length - fets.size} grups`)
    setSaving(false)
  }

  /** Afegeix (o treu) els alumnes triats al grup escollit, amb una sola crida. */
  const handleAlumnesLot = async (afegir: boolean) => {
    const grup = grups.find(g => g.id === grupDestiLot)
    if (!grup || alumnesTriats.size === 0) return
    setSaving(true); setError('')
    try {
      const actuals = new Set(grup.students.map(s => s.id))
      alumnesTriats.forEach(id => (afegir ? actuals.add(id) : actuals.delete(id)))
      const updated = await setGrupStudents(grup.id, [...actuals])
      setGrups(prev => prev.map(g => g.id === updated.id ? updated : g))
      setAlumnesTriats(new Set())
    } catch { setError('Error desant els alumnes del grup') }
    finally { setSaving(false) }
  }

  /** Només l'administrador: elimina els alumnes triats (amb «forçar», també les seves dades d'exàmens). */
  const handleEliminaAlumnes = async () => {
    const ids = [...alumnesTriats]
    if (!ids.length) return
    const n = `${ids.length} alumne${ids.length > 1 ? 's' : ''}`
    if (!confirm(forcaEliminar
      ? `Eliminar ${n} I TOTES LES SEVES DADES D'EXÀMENS (sessions, respostes i notes)? Aquesta acció no es pot desfer.`
      : `Eliminar ${n}? Aquesta acció no es pot desfer.`)) return
    setSaving(true); setError('')
    try {
      const r = await deleteUsers(ids, forcaEliminar)
      const fets = new Set(r.eliminats)
      setStudents(prev => prev.filter(a => !fets.has(a.id)))
      setGrups(prev => prev.map(g => ({ ...g, students: g.students.filter(a => !fets.has(a.id)) })))
      setAlumnesTriats(new Set(r.errors.map(e => e.id)))
      if (r.errors.length) setError(`S'han eliminat ${fets.size}; ${r.errors.length} no s'han pogut eliminar: `
        + r.errors.map(e => e.motiu).join(' · '))
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'han pogut eliminar els alumnes')
    } finally { setSaving(false) }
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
                <>
                {grupsTriats.size > 0 && (
                  <div className="flex items-center gap-3 text-sm">
                    <span className="text-gray-500">{grupsTriats.size} seleccionat{grupsTriats.size !== 1 ? 's' : ''}</span>
                    <button onClick={handleDeleteGrupsTriats} disabled={saving}
                      className="border border-red-200 rounded-lg px-3 py-1.5 hover:bg-red-50 text-red-600 disabled:opacity-50">
                      Eliminar seleccionats
                    </button>
                  </div>
                )}
                <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 w-8">
                          <input type="checkbox" checked={tots(grups, grupsTriats)}
                            onChange={() => commutaTots(grups, setGrupsTriats)}
                            aria-label="Seleccionar o deseleccionar tots els grups" />
                        </th>
                        <SortTh clau="nom" {...ordreGrups}>Grup</SortTh>
                        <SortTh clau="modul" {...ordreGrups}>Mòdul</SortTh>
                        <SortTh clau="alumnes" {...ordreGrups}>Alumnes</SortTh>
                        <th className="px-4 py-3"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {grupsOrdenats.map(g => (
                        <tr key={g.id} className="hover:bg-gray-50">
                          <td className="px-4 py-3">
                            <input type="checkbox" checked={grupsTriats.has(g.id)}
                              onChange={() => setGrupsTriats(prev => toggle(prev, g.id))}
                              aria-label={`Seleccionar ${g.name}`} />
                          </td>
                          <td className="px-4 py-3 font-semibold text-gray-800">{g.name}</td>
                          <td className="px-4 py-3">
                            {g.modulNom
                              ? <span className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full font-mono">{g.modulNom}</span>
                              : <span className="text-gray-300 text-xs italic">Sense mòdul</span>}
                          </td>
                          <td className="px-4 py-3 text-xs text-gray-500">
                            {g.students.length === 0
                              ? 'Sense alumnes'
                              : `${g.students.length}: ${g.students.slice(0, 3).map(s => s.name).join(', ')}${g.students.length > 3 ? '…' : ''}`}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex gap-2 justify-end">
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
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                </>
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
                {alumnesTriats.size > 0 && (
                  <div className="flex flex-wrap items-center gap-2 text-sm mb-3">
                    <span className="text-gray-500">{alumnesTriats.size} seleccionat{alumnesTriats.size !== 1 ? 's' : ''}</span>
                    <select value={grupDestiLot} onChange={e => setGrupDestiLot(e.target.value)} aria-label="Grup"
                      className="border border-gray-300 rounded-lg px-2 py-1.5 text-sm">
                      <option value="">Tria un grup…</option>
                      {grups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                    </select>
                    <button onClick={() => handleAlumnesLot(true)} disabled={saving || !grupDestiLot}
                      className="bg-brand-600 text-white rounded-lg px-3 py-1.5 hover:bg-brand-700 disabled:opacity-50">
                      Afegir al grup
                    </button>
                    <button onClick={() => handleAlumnesLot(false)} disabled={saving || !grupDestiLot}
                      className="border border-gray-300 rounded-lg px-3 py-1.5 hover:bg-gray-50 text-gray-700 disabled:opacity-50">
                      Treure del grup
                    </button>
                  </div>
                )}
                {esAdmin && (
                  <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
                    <label className="flex items-center gap-1 text-xs text-red-700"
                      title="Elimina també les sessions d'examen dels alumnes (respostes i notes)">
                      <input type="checkbox" checked={forcaEliminar} onChange={e => setForcaEliminar(e.target.checked)} />
                      Forçar: eliminar també les dades dels exàmens
                    </label>
                    <button onClick={handleEliminaAlumnes} disabled={saving || alumnesTriats.size === 0}
                      className="bg-red-600 text-white rounded-lg px-3 py-1.5 hover:bg-red-700 disabled:opacity-40">
                      Eliminar alumnes seleccionats{alumnesTriats.size ? ` (${alumnesTriats.size})` : ''}
                    </button>
                  </div>
                )}
                <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-4 py-3 w-8">
                          <input type="checkbox" checked={tots(alumnesTab, alumnesTriats)}
                            onChange={() => commutaTots(alumnesTab, setAlumnesTriats)}
                            aria-label="Seleccionar o deseleccionar tots els alumnes visibles" />
                        </th>
                        <SortTh clau="nom" {...ordreAlumnes}>Alumne</SortTh>
                        <SortTh clau="correu" {...ordreAlumnes}>Correu</SortTh>
                        <SortTh clau="grups" {...ordreAlumnes}>Grups</SortTh>
                        <th className="px-4 py-3 w-24"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {alumnesTab.length === 0 && (
                        <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                          Cap alumne coincideix amb els filtres.
                        </td></tr>
                      )}
                      {alumnesOrdenats.map(s => {
                        const myGrups = grups.filter(g => g.students.some(x => x.id === s.id))
                        return (
                          <tr key={s.id} className="hover:bg-gray-50">
                            <td className="px-4 py-3">
                              <input type="checkbox" checked={alumnesTriats.has(s.id)}
                                onChange={() => setAlumnesTriats(prev => toggle(prev, s.id))}
                                aria-label={`Seleccionar ${s.name}`} />
                            </td>
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
                <div className="flex justify-end gap-3 text-xs mt-2">
                  <button type="button" disabled={grupsVisiblesModal.length === 0}
                    onClick={() => setSelectedGrups(prev => { const n = new Set(prev); grupsVisiblesModal.forEach(g => n.add(g.id)); return n })}
                    className="text-brand-600 hover:underline disabled:opacity-40">Marcar tots</button>
                  <button type="button" disabled={grupsVisiblesModal.length === 0}
                    onClick={() => setSelectedGrups(prev => { const n = new Set(prev); grupsVisiblesModal.forEach(g => n.delete(g.id)); return n })}
                    className="text-gray-500 hover:underline disabled:opacity-40">Desmarcar tots</button>
                </div>
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

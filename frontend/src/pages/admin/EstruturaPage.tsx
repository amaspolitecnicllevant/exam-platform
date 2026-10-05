import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getDepartaments, createDepartament, deleteDepartament, getProfessorsDepartament, addProfessorDepartament, removeProfessorDepartament } from '../../api/departaments'
import { getCicles, createCicle, updateCicle, deleteCicle } from '../../api/cicles'
import { getModuls, createModul, updateModul, deleteModul, getImparticions, addImparticio, removeImparticio } from '../../api/moduls'
import { getUsers } from '../../api/users'
import type { Departament, Cicle, Modul, Imparticio, ProfessorDepartament, User } from '../../types'

type Tab = 'departaments' | 'cicles' | 'moduls'

export default function EstruturaPage() {
  const [tab, setTab]               = useState<Tab>('departaments')
  const [deps, setDeps]             = useState<Departament[]>([])
  const [cicles, setCicles]         = useState<Cicle[]>([])
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [professors, setProfessors] = useState<User[]>([])
  const [error, setError]           = useState('')

  // Formularis
  const [newDepNom, setNewDepNom]   = useState('')
  const [newCicleCodi, setNewCicleCodi] = useState('')
  const [newCicleNom, setNewCicleNom]   = useState('')
  const [newCicleDep, setNewCicleDep]   = useState('')
  const [newModulCodi, setNewModulCodi] = useState('')
  const [newModulNom, setNewModulNom]   = useState('')
  const [newModulCicle, setNewModulCicle] = useState('')

  // Edició inline
  const [editCicle, setEditCicle]       = useState<Cicle | null>(null)
  const [editCicleCodi, setEditCicleCodi] = useState('')
  const [editCicleNom, setEditCicleNom]   = useState('')
  const [editModul, setEditModul]       = useState<Modul | null>(null)
  const [editModulCodi, setEditModulCodi] = useState('')
  const [editModulNom, setEditModulNom]   = useState('')

  // Modal imparticions
  const [impModul, setImpModul]         = useState<Modul | null>(null)
  const [imparticions, setImparticions] = useState<Imparticio[]>([])
  const [impProf, setImpProf]           = useState('')
  const [impCurs, setImpCurs]           = useState(currentCurs())

  // Modal professors departament
  const [profDepDept, setProfDepDept]       = useState<Departament | null>(null)
  const [profDepList, setProfDepList]       = useState<ProfessorDepartament[]>([])
  const [profDepSelProf, setProfDepSelProf] = useState('')
  const [profDepEsCap, setProfDepEsCap]     = useState(false)

  useEffect(() => {
    Promise.all([getDepartaments(), getCicles(), getModuls(), getUsers()])
      .then(([d, c, m, u]) => {
        setDeps(d); setCicles(c); setModuls(m)
        setProfessors(u.filter(u => u.role === 'PROFESSOR' || u.role === 'ADMIN'))
      })
      .catch(() => setError('Error carregant dades'))
  }, [])

  const err = (e: unknown) => setError(e instanceof Error ? e.message : 'Error')

  // ── Departaments ──────────────────────────────────────────────────────────

  const handleCreateDep = async (e: React.FormEvent) => {
    e.preventDefault(); if (!newDepNom.trim()) return
    try {
      const d = await createDepartament(newDepNom.trim())
      setDeps(prev => [...prev, d].sort((a, b) => a.nom.localeCompare(b.nom)))
      setNewDepNom('')
    } catch (e) { err(e) }
  }

  const handleDeleteDep = async (d: Departament) => {
    if (!confirm(`Eliminar el departament "${d.nom}"?`)) return
    try { await deleteDepartament(d.id); setDeps(prev => prev.filter(x => x.id !== d.id)) }
    catch (e) { err(e) }
  }

  const openProfDep = async (d: Departament) => {
    setProfDepDept(d); setProfDepSelProf(''); setProfDepEsCap(false)
    const list = await getProfessorsDepartament(d.id); setProfDepList(list)
  }

  const handleAddProfDep = async () => {
    if (!profDepDept || !profDepSelProf) return
    try {
      const pd = await addProfessorDepartament(profDepDept.id, profDepSelProf, profDepEsCap)
      setProfDepList(prev => [...prev, pd]); setProfDepSelProf(''); setProfDepEsCap(false)
    } catch (e) { err(e) }
  }

  const handleRemoveProfDep = async (pd: ProfessorDepartament) => {
    if (!profDepDept) return
    try {
      await removeProfessorDepartament(profDepDept.id, pd.professorId)
      setProfDepList(prev => prev.filter(x => x.professorId !== pd.professorId))
    } catch (e) { err(e) }
  }

  // ── Cicles ────────────────────────────────────────────────────────────────

  const startEditCicle = (c: Cicle) => {
    setEditCicle(c); setEditCicleCodi(c.codi); setEditCicleNom(c.nom)
  }

  const handleUpdateCicle = async (e: React.FormEvent) => {
    e.preventDefault(); if (!editCicle) return
    try {
      const updated = await updateCicle(editCicle.id, editCicleCodi, editCicleNom, editCicle.departamentId)
      setCicles(prev => prev.map(c => c.id === updated.id ? updated : c))
      setEditCicle(null)
    } catch (e) { err(e) }
  }

  const handleCreateCicle = async (e: React.FormEvent) => {
    e.preventDefault(); if (!newCicleCodi || !newCicleNom || !newCicleDep) return
    try {
      const c = await createCicle(newCicleCodi.trim(), newCicleNom.trim(), newCicleDep)
      setCicles(prev => [...prev, c].sort((a, b) => a.codi.localeCompare(b.codi)))
      setNewCicleCodi(''); setNewCicleNom('')
    } catch (e) { err(e) }
  }

  const handleDeleteCicle = async (c: Cicle) => {
    if (!confirm(`Eliminar el cicle "${c.codi}"?`)) return
    try { await deleteCicle(c.id); setCicles(prev => prev.filter(x => x.id !== c.id)) }
    catch (e) { err(e) }
  }

  // ── Mòduls ────────────────────────────────────────────────────────────────

  const startEditModul = (m: Modul) => {
    setEditModul(m); setEditModulCodi(m.codi); setEditModulNom(m.nom)
  }

  const handleUpdateModul = async (e: React.FormEvent) => {
    e.preventDefault(); if (!editModul) return
    try {
      const updated = await updateModul(editModul.id, editModulCodi, editModulNom, editModul.cicleId)
      setModuls(prev => prev.map(m => m.id === updated.id ? updated : m))
      setEditModul(null)
    } catch (e) { err(e) }
  }

  const handleCreateModul = async (e: React.FormEvent) => {
    e.preventDefault(); if (!newModulCodi || !newModulNom || !newModulCicle) return
    try {
      const m = await createModul(newModulCodi.trim(), newModulNom.trim(), newModulCicle)
      setModuls(prev => [...prev, m].sort((a, b) => a.codi.localeCompare(b.codi)))
      setNewModulCodi(''); setNewModulNom('')
    } catch (e) { err(e) }
  }

  const handleDeleteModul = async (m: Modul) => {
    if (!confirm(`Eliminar el mòdul "${m.codi}"?`)) return
    try { await deleteModul(m.id); setModuls(prev => prev.filter(x => x.id !== m.id)) }
    catch (e) { err(e) }
  }

  const openImparticions = async (m: Modul) => {
    setImpModul(m); setImpProf('')
    const imp = await getImparticions(m.id); setImparticions(imp)
  }

  const handleAddImparticio = async () => {
    if (!impModul || !impProf || !impCurs) return
    try {
      const i = await addImparticio(impModul.id, impProf, impCurs)
      setImparticions(prev => [...prev, i])
      setImpProf('')
    } catch (e) { err(e) }
  }

  const handleRemoveImparticio = async (i: Imparticio) => {
    if (!impModul) return
    try {
      await removeImparticio(impModul.id, i.id)
      setImparticions(prev => prev.filter(x => x.id !== i.id))
    } catch (e) { err(e) }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <Layout>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-brand-700">Estructura acadèmica</h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2 text-sm flex justify-between">
            <span>{error}</span>
            <button onClick={() => setError('')} className="font-bold">×</button>
          </div>
        )}

        {/* Tabs */}
        <div className="flex border-b border-gray-200">
          {([
            ['departaments', `Departaments (${deps.length})`],
            ['cicles', `Cicles (${cicles.length})`],
            ['moduls', `Mòduls (${moduls.length})`],
          ] as [Tab, string][]).map(([t, label]) => (
            <button key={t} onClick={() => setTab(t)}
              className={`px-5 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
                tab === t ? 'border-brand-600 text-brand-700' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>
              {label}
            </button>
          ))}
        </div>

        {/* ── Tab: Departaments ── */}
        {tab === 'departaments' && (
          <div className="space-y-4">
            <form onSubmit={handleCreateDep} className="flex gap-2">
              <input type="text" value={newDepNom} onChange={e => setNewDepNom(e.target.value)}
                placeholder="Nom del departament" required
                className="flex-1 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <button type="submit" disabled={!newDepNom.trim()}
                className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-brand-700 disabled:opacity-50">
                Crear
              </button>
            </form>
            <div className="bg-white border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium text-gray-600">Nom</th>
                    <th className="px-4 py-3 w-40"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {deps.length === 0 && (
                    <tr><td colSpan={2} className="px-4 py-6 text-center text-gray-400 text-xs">Sense departaments</td></tr>
                  )}
                  {deps.map(d => (
                    <tr key={d.id} className="hover:bg-gray-50">
                      <td className="px-4 py-2 text-gray-800">{d.nom}</td>
                      <td className="px-4 py-2 flex gap-2 justify-end">
                        <button onClick={() => openProfDep(d)}
                          className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                          Professors
                        </button>
                        <button onClick={() => handleDeleteDep(d)}
                          className="text-xs border border-red-200 rounded px-2 py-1 hover:bg-red-50 text-red-600">
                          Eliminar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Tab: Cicles ── */}
        {tab === 'cicles' && (
          <div className="space-y-4">
            <form onSubmit={handleCreateCicle} className="grid grid-cols-3 gap-2">
              <input type="text" value={newCicleCodi} onChange={e => setNewCicleCodi(e.target.value)}
                placeholder="Codi (p. ex. ASIX)" required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <input type="text" value={newCicleNom} onChange={e => setNewCicleNom(e.target.value)}
                placeholder="Nom del cicle" required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <select value={newCicleDep} onChange={e => setNewCicleDep(e.target.value)} required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500">
                <option value="">Departament…</option>
                {deps.map(d => <option key={d.id} value={d.id}>{d.nom}</option>)}
              </select>
              <button type="submit" disabled={!newCicleCodi || !newCicleNom || !newCicleDep}
                className="col-span-3 bg-brand-600 text-white rounded-lg py-2 text-sm font-medium hover:bg-brand-700 disabled:opacity-50">
                Crear cicle
              </button>
            </form>
            <div className="bg-white border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    {['Codi', 'Nom', 'Departament', ''].map(h => (
                      <th key={h} className="px-4 py-3 text-left font-medium text-gray-600">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {cicles.length === 0 && (
                    <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400 text-xs">Sense cicles</td></tr>
                  )}
                  {cicles.map(c => (
                    <tr key={c.id} className="hover:bg-gray-50">
                      {editCicle?.id === c.id ? (
                        <>
                          <td className="px-4 py-2">
                            <input value={editCicleCodi} onChange={e => setEditCicleCodi(e.target.value)}
                              className="border rounded px-2 py-1 text-sm w-24 focus:ring-2 focus:ring-brand-500 focus:outline-none" />
                          </td>
                          <td className="px-4 py-2" colSpan={2}>
                            <input value={editCicleNom} onChange={e => setEditCicleNom(e.target.value)}
                              className="border rounded px-2 py-1 text-sm w-full focus:ring-2 focus:ring-brand-500 focus:outline-none" />
                          </td>
                          <td className="px-4 py-2 flex gap-2 justify-end">
                            <button onClick={handleUpdateCicle as any}
                              className="text-xs bg-brand-600 text-white rounded px-2 py-1 hover:bg-brand-700">
                              Desar
                            </button>
                            <button onClick={() => setEditCicle(null)}
                              className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                              Cancel·lar
                            </button>
                          </td>
                        </>
                      ) : (
                        <>
                          <td className="px-4 py-2"><Badge>{c.codi}</Badge></td>
                          <td className="px-4 py-2 text-gray-800">{c.nom}</td>
                          <td className="px-4 py-2 text-gray-500">{c.departamentNom}</td>
                          <td className="px-4 py-2 flex gap-2 justify-end">
                            <button onClick={() => startEditCicle(c)}
                              className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                              Editar
                            </button>
                            <button onClick={() => handleDeleteCicle(c)}
                              className="text-xs border border-red-200 rounded px-2 py-1 hover:bg-red-50 text-red-600">
                              Eliminar
                            </button>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── Tab: Mòduls ── */}
        {tab === 'moduls' && (
          <div className="space-y-4">
            <form onSubmit={handleCreateModul} className="grid grid-cols-3 gap-2">
              <input type="text" value={newModulCodi} onChange={e => setNewModulCodi(e.target.value)}
                placeholder="Codi (p. ex. 0483)" required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <input type="text" value={newModulNom} onChange={e => setNewModulNom(e.target.value)}
                placeholder="Nom del mòdul" required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
              <select value={newModulCicle} onChange={e => setNewModulCicle(e.target.value)} required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500">
                <option value="">Cicle…</option>
                {cicles.map(c => <option key={c.id} value={c.id}>{c.codi} — {c.nom}</option>)}
              </select>
              <button type="submit" disabled={!newModulCodi || !newModulNom || !newModulCicle}
                className="col-span-3 bg-brand-600 text-white rounded-lg py-2 text-sm font-medium hover:bg-brand-700 disabled:opacity-50">
                Crear mòdul
              </button>
            </form>
            <div className="bg-white border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    {['Codi', 'Nom', 'Cicle', 'Dept.', ''].map(h => (
                      <th key={h} className="px-4 py-3 text-left font-medium text-gray-600">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {moduls.length === 0 && (
                    <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400 text-xs">Sense mòduls</td></tr>
                  )}
                  {moduls.map(m => (
                    <tr key={m.id} className="hover:bg-gray-50">
                      {editModul?.id === m.id ? (
                        <>
                          <td className="px-4 py-2">
                            <input value={editModulCodi} onChange={e => setEditModulCodi(e.target.value)}
                              className="border rounded px-2 py-1 text-sm w-24 focus:ring-2 focus:ring-brand-500 focus:outline-none" />
                          </td>
                          <td className="px-4 py-2" colSpan={2}>
                            <input value={editModulNom} onChange={e => setEditModulNom(e.target.value)}
                              className="border rounded px-2 py-1 text-sm w-full focus:ring-2 focus:ring-brand-500 focus:outline-none" />
                          </td>
                          <td className="px-4 py-2 text-gray-400 text-xs">{m.departamentNom}</td>
                          <td className="px-4 py-2 flex gap-2 justify-end">
                            <button onClick={handleUpdateModul as any}
                              className="text-xs bg-brand-600 text-white rounded px-2 py-1 hover:bg-brand-700">
                              Desar
                            </button>
                            <button onClick={() => setEditModul(null)}
                              className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                              Cancel·lar
                            </button>
                          </td>
                        </>
                      ) : (
                        <>
                          <td className="px-4 py-2"><Badge>{m.codi}</Badge></td>
                          <td className="px-4 py-2 text-gray-800">{m.nom}</td>
                          <td className="px-4 py-2 text-gray-500">{m.cicleNom}</td>
                          <td className="px-4 py-2 text-gray-400 text-xs">{m.departamentNom}</td>
                          <td className="px-4 py-2 flex gap-2 justify-end">
                            <button onClick={() => startEditModul(m)}
                              className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                              Editar
                            </button>
                            <button onClick={() => openImparticions(m)}
                              className="text-xs border border-gray-300 rounded px-2 py-1 hover:bg-gray-100">
                              Imparticions
                            </button>
                            <button onClick={() => handleDeleteModul(m)}
                              className="text-xs border border-red-200 rounded px-2 py-1 hover:bg-red-50 text-red-600">
                              Eliminar
                            </button>
                          </td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {/* Modal professors departament */}
      {profDepDept && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[80vh] flex flex-col">
            <div className="px-6 py-4 border-b">
              <h2 className="font-semibold">Professors — {profDepDept.nom}</h2>
              <p className="text-xs text-gray-500 mt-0.5">Un professor pot pertànyer a més d'un departament.</p>
            </div>
            <div className="flex-1 overflow-y-auto px-6 py-4 space-y-3">
              <div className="flex gap-2 flex-wrap">
                <select value={profDepSelProf} onChange={e => setProfDepSelProf(e.target.value)}
                  className="flex-1 min-w-48 border rounded px-2 py-1.5 text-sm">
                  <option value="">Professor…</option>
                  {professors
                    .filter(p => !profDepList.some(pd => pd.professorId === p.id))
                    .map(p => <option key={p.id} value={p.id}>{p.name} ({p.email})</option>)}
                </select>
                <label className="flex items-center gap-1.5 text-sm text-gray-700 cursor-pointer">
                  <input type="checkbox" checked={profDepEsCap} onChange={e => setProfDepEsCap(e.target.checked)}
                    className="accent-brand-600" />
                  Cap de departament
                </label>
                <button onClick={handleAddProfDep} disabled={!profDepSelProf}
                  className="bg-brand-600 text-white rounded px-3 py-1.5 text-sm hover:bg-brand-700 disabled:opacity-50">
                  Afegir
                </button>
              </div>
              {profDepList.length === 0
                ? <p className="text-sm text-gray-400 text-center py-4">Sense professors assignats.</p>
                : profDepList.map(pd => (
                  <div key={pd.professorId} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-2">
                    <div>
                      <p className="text-sm font-medium text-gray-800">
                        {pd.professorNom}
                        {pd.esCap && <span className="ml-2 text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">Cap</span>}
                      </p>
                      <p className="text-xs text-gray-500">{pd.professorEmail}</p>
                    </div>
                    <button onClick={() => handleRemoveProfDep(pd)}
                      className="text-red-500 hover:text-red-700 text-xs">Eliminar</button>
                  </div>
                ))}
            </div>
            <div className="px-6 py-4 border-t flex justify-end">
              <button onClick={() => setProfDepDept(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm hover:bg-gray-50">
                Tancar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal imparticions */}
      {impModul && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg max-h-[80vh] flex flex-col">
            <div className="px-6 py-4 border-b">
              <h2 className="font-semibold">Imparticions — {impModul.codi}</h2>
              <p className="text-xs text-gray-500 mt-0.5">{impModul.nom}</p>
            </div>
            <div className="flex-1 overflow-y-auto px-6 py-4 space-y-3">
              {/* Afegir */}
              <div className="flex gap-2">
                <select value={impProf} onChange={e => setImpProf(e.target.value)}
                  className="flex-1 border rounded px-2 py-1.5 text-sm">
                  <option value="">Professor…</option>
                  {professors.map(p => <option key={p.id} value={p.id}>{p.name} ({p.email})</option>)}
                </select>
                <input type="text" value={impCurs} onChange={e => setImpCurs(e.target.value)}
                  placeholder="Curs (p. ex. 2026-27)"
                  className="w-28 border rounded px-2 py-1.5 text-sm" />
                <button onClick={handleAddImparticio} disabled={!impProf || !impCurs}
                  className="bg-brand-600 text-white rounded px-3 py-1.5 text-sm hover:bg-brand-700 disabled:opacity-50">
                  Afegir
                </button>
              </div>
              {/* Llista */}
              {imparticions.length === 0
                ? <p className="text-sm text-gray-400 text-center py-4">Sense imparticions registrades.</p>
                : imparticions.map(i => (
                  <div key={i.id} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-2">
                    <div>
                      <p className="text-sm font-medium text-gray-800">{i.professorNom}</p>
                      <p className="text-xs text-gray-500">Curs {i.curs}</p>
                    </div>
                    <button onClick={() => handleRemoveImparticio(i)}
                      className="text-red-500 hover:text-red-700 text-xs">Eliminar</button>
                  </div>
                ))}
            </div>
            <div className="px-6 py-4 border-t flex justify-end">
              <button onClick={() => setImpModul(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm hover:bg-gray-50">
                Tancar
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}

// ── Helpers UI ──────────────────────────────────────────────────────────────

function Badge({ children }: { children: React.ReactNode }) {
  return (
    <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded font-mono font-medium">
      {children}
    </span>
  )
}


function currentCurs(): string {
  const now = new Date()
  const yr = now.getFullYear()
  const inici = now.getMonth() >= 8 ? yr : yr - 1
  return `${inici}-${String(inici + 1).slice(2)}`
}

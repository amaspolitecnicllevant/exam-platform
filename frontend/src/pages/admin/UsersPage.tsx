import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getUsers, createUser, deleteUser, deleteUsers, resetPassword, revocaSessions, minimContrasenya } from '../../api/users'
import { useAuth } from '../../context/AuthContext'
import { SortTh, useOrdre, ordena } from '../../components/SortTh'
import ImportacioUsuaris from '../../components/ImportacioUsuaris'
import type { User, Role } from '../../types'

export default function UsersPage() {
  const [users, setUsers]     = useState<User[]>([])
  const [form, setForm]       = useState({ name: '', email: '', password: '', role: 'STUDENT' as Role })
  const [error, setError]     = useState('')
  const { user: yo }          = useAuth()
  const [cerca, setCerca]     = useState('')
  const [filtreRol, setFiltreRol] = useState<'' | Role>('')
  const [seleccio, setSeleccio]   = useState<Set<string>>(new Set())
  const ordreUsuaris = useOrdre<'name' | 'email' | 'role'>('name')
  const [eliminant, setEliminant] = useState(false)
  const [forca, setForca]        = useState(false)

  // Modal reset contrasenya
  const [resetUser, setResetUser]   = useState<User | null>(null)
  const [newPwd, setNewPwd]         = useState('')
  const [resetMsg, setResetMsg]     = useState('')
  const [resetting, setResetting]   = useState(false)

  useEffect(() => { getUsers().then(setUsers) }, [])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const u = await createUser(form)
      setUsers(prev => [...prev, u])
      setForm({ name: '', email: '', password: '', role: 'STUDENT' })
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut crear l\'usuari')
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm(forca ? 'Eliminar l\'usuari i totes les seves dades d\'exàmens? Aquesta acció no es pot desfer.' : 'Eliminar usuari?')) return
    setError('')
    try {
      await deleteUser(id, forca)
      setUsers(prev => prev.filter(u => u.id !== id))
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut eliminar l\'usuari')
    }
  }

  const visibles = users.filter(u =>
    (!filtreRol || u.role === filtreRol) &&
    (!cerca || (u.name + ' ' + u.email).toLowerCase().includes(cerca.toLowerCase())))
  const ordenats = ordena(visibles, ordreUsuaris.ordre, (u, k) => u[k])
  const seleccionables = visibles.filter(u => u.id !== yo?.userId)
  const totsSeleccionats = seleccionables.length > 0 && seleccionables.every(u => seleccio.has(u.id))

  const toggle = (id: string) => setSeleccio(prev => {
    const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const toggleTots = () => setSeleccio(prev => {
    const n = new Set(prev)
    seleccionables.forEach(u => totsSeleccionats ? n.delete(u.id) : n.add(u.id))
    return n
  })

  const handleDeleteSeleccio = async () => {
    const ids = [...seleccio]
    if (!ids.length) return
    const msg = forca
      ? `Eliminar ${ids.length} usuari${ids.length > 1 ? 's' : ''} I TOTES LES SEVES DADES D'EXÀMENS (sessions, respostes i notes; si algun és professor, també els exàmens i grups que ha creat, amb les notes de tots els alumnes)? Aquesta acció no es pot desfer.`
      : `Eliminar ${ids.length} usuari${ids.length > 1 ? 's' : ''}? Aquesta acció no es pot desfer.`
    if (!confirm(msg)) return
    setError(''); setEliminant(true)
    try {
      const r = await deleteUsers(ids, forca)
      const fets = new Set(r.eliminats)
      setUsers(prev => prev.filter(u => !fets.has(u.id)))
      setSeleccio(new Set(r.errors.map(e => e.id)))
      if (r.errors.length) {
        setError(`S'han eliminat ${fets.size} usuaris; ${r.errors.length} no s'han pogut eliminar: ` +
          r.errors.map(e => e.motiu).join(' · '))
      }
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'han pogut eliminar els usuaris')
    } finally {
      setEliminant(false)
    }
  }

  const openReset = (u: User) => {
    setResetUser(u)
    setNewPwd('')
    setResetMsg('')
  }

  const handleReset = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!resetUser) return
    setResetting(true); setResetMsg('')
    try {
      await resetPassword(resetUser.id, newPwd)
      setResetMsg('Contrasenya canviada correctament.')
      setNewPwd('')
    } catch (err: any) {
      setResetMsg('Error: ' + (err?.response?.data?.error || 'no s\'ha pogut canviar la contrasenya.'))
    } finally {
      setResetting(false)
    }
  }

  const handleRevoca = async (u: User) => {
    if (!confirm(`Tancar totes les sessions obertes de ${u.name}? Haurà de tornar a entrar amb la seva contrasenya.`)) return
    try { await revocaSessions(u.id); alert('Sessions tancades.') } catch { alert('No s\'han pogut tancar les sessions.') }
  }

  return (
    <Layout>
      <div className="space-y-8">
        <h1 className="text-2xl font-bold text-brand-700">Gestió d'usuaris</h1>

        {/* Importació CSV */}
        <ImportacioUsuaris onImportat={() => getUsers().then(setUsers)} />

        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

        {/* Nou usuari */}
        <div className="bg-white rounded-xl border p-6">
          <h2 className="font-semibold mb-3">Nou usuari</h2>
          <form onSubmit={handleCreate} className="grid grid-cols-2 gap-3">
            {(['name','email','password'] as const).map(f => (
              <input key={f} type={f === 'password' ? 'password' : f === 'email' ? 'email' : 'text'}
                placeholder={f} value={form[f]}
                onChange={e => setForm(p => ({ ...p, [f]: e.target.value }))} required
                className="border rounded px-3 py-2 text-sm" />
            ))}
            <select value={form.role} onChange={e => setForm(p => ({ ...p, role: e.target.value as Role }))}
              className="border rounded px-3 py-2 text-sm">
              {(['ADMIN','PROFESSOR','STUDENT'] as Role[]).map(r => <option key={r}>{r}</option>)}
            </select>
            <button type="submit"
              className="col-span-2 bg-brand-600 text-white rounded py-2 text-sm hover:bg-brand-700">
              Crear usuari
            </button>
          </form>
        </div>

        {/* Llistat */}
        <div className="flex flex-wrap items-center gap-3">
          <input type="search" placeholder="Cerca per nom o correu" value={cerca} onChange={e => setCerca(e.target.value)}
            className="border rounded px-3 py-2 text-sm" />
          <select value={filtreRol} onChange={e => setFiltreRol(e.target.value as '' | Role)}
            className="border rounded px-3 py-2 text-sm">
            <option value="">Tots els rols</option>
            {(['ADMIN','PROFESSOR','STUDENT'] as Role[]).map(r => <option key={r}>{r}</option>)}
          </select>
          <span className="text-xs text-gray-500">{visibles.length} usuaris</span>
          <label className="flex items-center gap-1 text-xs text-red-700" title="Elimina també les sessions d'examen de l'usuari i, si és professor, els exàmens i grups que ha creat">
            <input type="checkbox" checked={forca} onChange={e => setForca(e.target.checked)} />
            Forçar: eliminar també les dades dels exàmens
          </label>
          {seleccio.size > 0 && (
            <button onClick={handleDeleteSeleccio} disabled={eliminant}
              className="ml-auto bg-red-600 text-white rounded px-4 py-2 text-sm hover:bg-red-700 disabled:opacity-50">
              {eliminant ? 'Eliminant…' : `Eliminar seleccionats (${seleccio.size})`}
            </button>
          )}
        </div>
        <div className="bg-white rounded-xl border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-brand-600 text-white">
              <tr>
                <th className="px-4 py-2 w-8">
                  <input type="checkbox" checked={totsSeleccionats} onChange={toggleTots}
                    aria-label="Seleccionar tots els usuaris visibles" />
                </th>
                <SortTh clau="name" {...ordreUsuaris} className="!text-white">Nom</SortTh>
                <SortTh clau="email" {...ordreUsuaris} className="!text-white">Correu</SortTh>
                <SortTh clau="role" {...ordreUsuaris} className="!text-white">Rol</SortTh>
                <th />
              </tr>
            </thead>
            <tbody>
              {ordenats.map((u, i) => (
                <tr key={u.id} className={i % 2 ? 'bg-brand-50' : ''}>
                  <td className="px-4 py-2">
                    <input type="checkbox" checked={seleccio.has(u.id)} disabled={u.id === yo?.userId}
                      onChange={() => toggle(u.id)} aria-label={`Seleccionar ${u.name}`} />
                  </td>
                  <td className="px-4 py-2">{u.name}</td>
                  <td className="px-4 py-2">{u.email}</td>
                  <td className="px-4 py-2"><span className="bg-brand-100 text-brand-700 px-2 py-0.5 rounded text-xs">{u.role}</span></td>
                  <td className="px-4 py-2 flex gap-2 justify-end">
                    <button onClick={() => openReset(u)}
                      className="text-brand-600 hover:underline text-xs">
                      Contrasenya
                    </button>
                    <button onClick={() => handleRevoca(u)} title="Tanca totes les sessions obertes d'aquest usuari"
                      className="text-amber-700 hover:underline text-xs">
                      Tancar sessions
                    </button>
                    <button onClick={() => handleDelete(u.id)}
                      className="text-red-600 hover:underline text-xs">
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal reset contrasenya */}
      {resetUser && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Canviar contrasenya</h2>
              <p className="text-xs text-gray-500 mt-0.5">{resetUser.name} · {resetUser.email}</p>
            </div>
            <form onSubmit={handleReset} className="px-6 py-4 space-y-3">
              <input
                type="password"
                placeholder={`Nova contrasenya (mínim ${minimContrasenya(resetUser.role)} caràcters)`}
                value={newPwd}
                onChange={e => setNewPwd(e.target.value)}
                required minLength={minimContrasenya(resetUser.role)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500"
              />
              {resetMsg && (
                <p className={`text-xs ${resetMsg.includes('Error') ? 'text-red-600' : 'text-green-600'}`}>
                  {resetMsg}
                </p>
              )}
              <div className="flex justify-between pt-1">
                <button type="button" onClick={() => setResetUser(null)}
                  className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                  Tancar
                </button>
                <button type="submit" disabled={resetting || newPwd.length < minimContrasenya(resetUser.role)}
                  className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700 disabled:opacity-50">
                  {resetting ? 'Desant…' : 'Desar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </Layout>
  )
}

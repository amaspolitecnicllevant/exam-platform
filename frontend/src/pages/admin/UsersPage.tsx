import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getUsers, createUser, deleteUser, resetPassword } from '../../api/users'
import ImportacioUsuaris from '../../components/ImportacioUsuaris'
import type { User, Role } from '../../types'

export default function UsersPage() {
  const [users, setUsers]     = useState<User[]>([])
  const [form, setForm]       = useState({ name: '', email: '', password: '', role: 'STUDENT' as Role })
  const [error, setError]     = useState('')

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
    if (!confirm('Eliminar usuari?')) return
    setError('')
    try {
      await deleteUser(id)
      setUsers(prev => prev.filter(u => u.id !== id))
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut eliminar l\'usuari')
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
    } catch {
      setResetMsg('Error canviant la contrasenya.')
    } finally {
      setResetting(false)
    }
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
        <div className="bg-white rounded-xl border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-brand-600 text-white">
              <tr>{['Nom','Correu','Rol',''].map(h => <th key={h} className="px-4 py-2 text-left">{h}</th>)}</tr>
            </thead>
            <tbody>
              {users.map((u, i) => (
                <tr key={u.id} className={i % 2 ? 'bg-brand-50' : ''}>
                  <td className="px-4 py-2">{u.name}</td>
                  <td className="px-4 py-2">{u.email}</td>
                  <td className="px-4 py-2"><span className="bg-brand-100 text-brand-700 px-2 py-0.5 rounded text-xs">{u.role}</span></td>
                  <td className="px-4 py-2 flex gap-2 justify-end">
                    <button onClick={() => openReset(u)}
                      className="text-brand-600 hover:underline text-xs">
                      Contrasenya
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
                placeholder="Nova contrasenya (mínim 8 caràcters)"
                value={newPwd}
                onChange={e => setNewPwd(e.target.value)}
                required minLength={8}
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
                <button type="submit" disabled={resetting || newPwd.length < 8}
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

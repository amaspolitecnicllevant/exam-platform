import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getAules, createAula, updateAula, deleteAula } from '../../api/aules'
import type { Aula } from '../../types'

const CIDR_RE = /^(\d{1,3}\.){3}\d{1,3}\/(\d|[1-2]\d|3[0-2])$/

function validate(nom: string, cidr: string): string | null {
  if (!nom.trim()) return 'El nom és obligatori'
  if (!CIDR_RE.test(cidr)) return 'El CIDR ha de tenir format x.x.x.x/n (p.ex. 10.0.1.0/24)'
  return null
}

export default function AulesPage() {
  const [aules, setAules]     = useState<Aula[]>([])
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing]   = useState<Aula | null>(null)
  const [nom, setNom]           = useState('')
  const [cidr, setCidr]         = useState('')
  const [error, setError]       = useState<string | null>(null)

  const refresh = () => getAules().then(setAules)

  useEffect(() => { refresh() }, [])

  function openCreate() {
    setEditing(null); setNom(''); setCidr(''); setError(null); setShowForm(true)
  }

  function openEdit(a: Aula) {
    setEditing(a); setNom(a.nom); setCidr(a.xarxaCidr); setError(null); setShowForm(true)
  }

  function closeForm() {
    setShowForm(false); setEditing(null); setNom(''); setCidr(''); setError(null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const err = validate(nom, cidr)
    if (err) { setError(err); return }
    setError(null)
    try {
      if (editing) {
        await updateAula(editing.id, nom.trim(), cidr.trim())
      } else {
        await createAula(nom.trim(), cidr.trim())
      }
      await refresh()
      closeForm()
    } catch (ex: unknown) {
      const data = (ex as { response?: { data?: { error?: string; message?: string } } }).response?.data
      const msg = data?.error ?? data?.message
      setError(msg ?? 'Error en desar l\'aula')
    }
  }

  async function handleDelete(a: Aula) {
    if (!confirm(`Esborrar l'aula "${a.nom}"?`)) return
    await deleteAula(a.id)
    refresh()
  }

  return (
    <Layout>
      <div className="p-6 max-w-3xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">Aules</h1>
          <button onClick={openCreate}
            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
            + Nova aula
          </button>
        </div>

        {showForm && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
            <div className="bg-white rounded-lg shadow-xl p-6 w-full max-w-md">
              <h2 className="text-lg font-semibold mb-4">
                {editing ? 'Editar aula' : 'Nova aula'}
              </h2>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Nom</label>
                  <input value={nom} onChange={e => setNom(e.target.value)}
                    placeholder="A101"
                    className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Xarxa CIDR</label>
                  <input value={cidr} onChange={e => setCidr(e.target.value)}
                    placeholder="10.0.1.0/24"
                    className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono" />
                  <p className="text-xs text-gray-500 mt-1">
                    Màquines d'aquesta subxarxa podran fer l'examen.
                  </p>
                </div>
                {error && <p className="text-red-600 text-sm">{error}</p>}
                <div className="flex gap-2 justify-end">
                  <button type="button" onClick={closeForm}
                    className="px-4 py-2 border rounded hover:bg-gray-50">
                    Cancel·lar
                  </button>
                  <button type="submit"
                    className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
                    {editing ? 'Desar' : 'Crear'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {aules.length === 0 ? (
          <p className="text-gray-500">No hi ha aules configurades.</p>
        ) : (
          <table className="w-full border-collapse">
            <thead>
              <tr className="bg-gray-50 text-left text-sm font-medium text-gray-600">
                <th className="px-4 py-3 border-b">Nom</th>
                <th className="px-4 py-3 border-b">Xarxa CIDR</th>
                <th className="px-4 py-3 border-b w-24"></th>
              </tr>
            </thead>
            <tbody>
              {aules.map(a => (
                <tr key={a.id} className="border-b hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium">{a.nom}</td>
                  <td className="px-4 py-3 font-mono text-sm text-gray-700">{a.xarxaCidr}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <button onClick={() => openEdit(a)}
                        className="text-blue-600 hover:underline text-sm">
                        Editar
                      </button>
                      <button onClick={() => handleDelete(a)}
                        className="text-red-600 hover:underline text-sm">
                        Esborrar
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Layout>
  )
}

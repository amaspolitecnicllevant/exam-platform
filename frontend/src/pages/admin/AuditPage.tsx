import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getAuditLogs, type AuditLogDto } from '../../api/audit'

const ACTIONS = ['LOGIN', 'LOGOUT', 'EXAM_CREATE', 'EXAM_PUBLISH', 'SESSION_START', 'SESSION_SUBMIT', 'USER_CREATE', 'USER_DISABLE']

export default function AuditPage() {
  const [logs, setLogs]         = useState<AuditLogDto[]>([])
  const [total, setTotal]       = useState(0)
  const [page, setPage]         = useState(0)
  const [filterAction, setFilterAction] = useState('')
  const [loading, setLoading]   = useState(false)
  const [error, setError]       = useState('')

  const PAGE_SIZE = 50

  useEffect(() => {
    setLoading(true); setError('')
    getAuditLogs({ action: filterAction || undefined, page, size: PAGE_SIZE })
      .then(p => { setLogs(p.content); setTotal(p.totalElements) })
      .catch(() => setError('Error carregant el registre'))
      .finally(() => setLoading(false))
  }, [page, filterAction])

  const totalPages = Math.ceil(total / PAGE_SIZE)

  const handleFilterChange = (v: string) => {
    setFilterAction(v)
    setPage(0)
  }

  return (
    <Layout>
      <div className="max-w-5xl">
        <h1 className="text-2xl font-bold text-brand-700 mb-6">Registre d'activitat</h1>

        <div className="flex items-center gap-3 mb-4">
          <select value={filterAction} onChange={e => handleFilterChange(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none">
            <option value="">Totes les accions</option>
            {ACTIONS.map(a => <option key={a} value={a}>{a}</option>)}
          </select>
          <span className="text-sm text-gray-500">{total} registres</span>
        </div>

        {error && <p className="text-sm text-red-600 mb-3">{error}</p>}

        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Data i hora</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Acció</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Recurs</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">IP</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Usuari (ID)</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">Carregant...</td></tr>
              ) : logs.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">Sense resultats</td></tr>
              ) : logs.map(l => (
                <tr key={l.id} className="hover:bg-gray-50">
                  <td className="px-4 py-2.5 font-mono text-xs text-gray-500 whitespace-nowrap">
                    {new Date(l.createdAt).toLocaleString('ca-ES')}
                  </td>
                  <td className="px-4 py-2.5">
                    <span className="inline-block bg-brand-100 text-brand-700 text-xs font-medium px-2 py-0.5 rounded">
                      {l.action}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-gray-600 max-w-xs truncate">{l.resource ?? '—'}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-gray-500">{l.ipAddress ?? '—'}</td>
                  <td className="px-4 py-2.5 font-mono text-xs text-gray-400 max-w-xs truncate">{l.userId ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between mt-4">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
              className="text-sm px-3 py-1.5 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
              Anterior
            </button>
            <span className="text-sm text-gray-500">Pàgina {page + 1} de {totalPages}</span>
            <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
              className="text-sm px-3 py-1.5 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
              Següent
            </button>
          </div>
        )}
      </div>
    </Layout>
  )
}

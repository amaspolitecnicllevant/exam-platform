import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getModuls } from '../../api/moduls'
import { getGrups } from '../../api/grups'
import { createInvitacio, getInvitacions, deactivateInvitacio } from '../../api/invitacions'
import { useConfiguracio } from '../../context/ConfiguracioContext'
import type { Modul, Invitacio, Grup } from '../../types'

function currentCurs(): string {
  const now = new Date(); const yr = now.getFullYear()
  const inici = now.getMonth() >= 8 ? yr : yr - 1
  return `${inici}-${String(inici + 1).slice(2)}`
}

function daysLeft(expiresAt: string): number {
  return Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 86_400_000)
}

const BASE_URL = window.location.origin

export default function ConvitsPage() {
  const { config } = useConfiguracio()
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [grups, setGrups]           = useState<Grup[]>([])
  const [invitacions, setInvitacions] = useState<Invitacio[]>([])
  const [loading, setLoading]       = useState(true)
  const [error, setError]           = useState('')
  const [copied, setCopied]         = useState<string | null>(null)

  const [selModul, setSelModul]     = useState('')
  const [selCurs, setSelCurs]       = useState(() => config?.cursActiu || currentCurs())
  const [selGrup, setSelGrup]       = useState('')
  const [creating, setCreating]     = useState(false)
  const [filtreModul, setFiltreModul] = useState('')
  const [filtreCurs, setFiltreCurs]   = useState('')

  useEffect(() => {
    if (config?.cursActiu) setSelCurs(c => c || config.cursActiu)
  }, [config?.cursActiu])

  // Reset grup selector when modul changes
  useEffect(() => { setSelGrup('') }, [selModul])

  useEffect(() => {
    Promise.all([getModuls(), getGrups(), getInvitacions()])
      .then(([m, g, i]) => { setModuls(m); setGrups(g); setInvitacions(i) })
      .catch(() => setError('Error carregant dades'))
      .finally(() => setLoading(false))
  }, [])

  const grupsDelModul = grups.filter(g => g.modulId === selModul)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selModul || !selCurs) return
    setCreating(true); setError('')
    try {
      const inv = await createInvitacio(selModul, selCurs, selGrup || undefined)
      setInvitacions(prev => [inv, ...prev])
      setSelGrup('')
    } catch (e) { setError(e instanceof Error ? e.message : 'Error creant el link') }
    finally { setCreating(false) }
  }

  const handleDeactivate = async (inv: Invitacio) => {
    if (!confirm('Desactivar aquest link? Deixarà de funcionar immediatament.')) return
    try {
      await deactivateInvitacio(inv.id)
      setInvitacions(prev => prev.map(i => i.id === inv.id ? { ...i, active: false } : i))
    } catch (e) { setError(e instanceof Error ? e.message : 'Error desactivant') }
  }

  const handleCopy = (token: string) => {
    const url = `${BASE_URL}/invitacio/${token}`
    navigator.clipboard.writeText(url)
    setCopied(token)
    setTimeout(() => setCopied(null), 2000)
  }

  // Opcions dels filtres: només els mòduls i cursos que tenen algun link
  const modulsAmbLinks = [...new Map(invitacions.map(i => [i.modulId, i])).values()]
    .sort((a, b) => a.modulCodi.localeCompare(b.modulCodi, 'ca'))
  const cursos = [...new Set(invitacions.map(i => i.curs))].sort().reverse()
  const filtrades = invitacions.filter(i =>
    (!filtreModul || i.modulId === filtreModul) && (!filtreCurs || i.curs === filtreCurs))
  const filtresActius = !!(filtreModul || filtreCurs)

  const actives = filtrades.filter(i => i.active)
  const inactives = filtrades.filter(i => !i.active)

  if (loading) return <Layout><p className="text-gray-400 p-8">Carregant…</p></Layout>

  return (
    <Layout>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-brand-700">Links de convit</h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-2 text-sm flex justify-between">
            <span>{error}</span>
            <button onClick={() => setError('')} className="font-bold">×</button>
          </div>
        )}

        {/* Formulari de creació */}
        <div className="bg-white border rounded-xl p-5 space-y-4">
          <h2 className="font-medium text-gray-800">Generar nou link</h2>
          <p className="text-xs text-gray-500">
            L'alumne obre el link, crea el compte i queda matriculat automàticament al mòdul.
            Si tries un grup, l'alumne també s'hi afegeix automàticament.
            El link és vàlid 7 dies.
          </p>
          <form onSubmit={handleCreate} className="flex flex-wrap gap-3 items-end">
            <div className="flex flex-col gap-1 flex-1 min-w-48">
              <label className="text-xs font-medium text-gray-600">Mòdul</label>
              <select value={selModul} onChange={e => setSelModul(e.target.value)} required
                className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500">
                <option value="">Tria un mòdul…</option>
                {moduls.map(m => (
                  <option key={m.id} value={m.id}>{m.codi} — {m.nom} ({m.cicleNom})</option>
                ))}
              </select>
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs font-medium text-gray-600">Curs</label>
              <input type="text" value={selCurs} onChange={e => setSelCurs(e.target.value)} required
                className="border rounded-lg px-3 py-2 text-sm w-28 focus:outline-none focus:ring-2 focus:ring-brand-500" />
            </div>
            {selModul && (
              <div className="flex flex-col gap-1 min-w-48">
                <label className="text-xs font-medium text-gray-600">
                  Grup <span className="text-gray-400 font-normal">(opcional)</span>
                </label>
                {grupsDelModul.length === 0 ? (
                  <p className="text-xs text-gray-400 py-2">
                    Cap grup assignat a aquest mòdul
                  </p>
                ) : (
                  <select value={selGrup} onChange={e => setSelGrup(e.target.value)}
                    className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500">
                    <option value="">Sense grup</option>
                    {grupsDelModul.map(g => (
                      <option key={g.id} value={g.id}>{g.name}</option>
                    ))}
                  </select>
                )}
              </div>
            )}
            <button type="submit" disabled={creating || !selModul || !selCurs}
              className="bg-brand-600 text-white rounded-lg px-5 py-2 text-sm font-medium hover:bg-brand-700 disabled:opacity-50">
              {creating ? 'Generant…' : 'Generar link'}
            </button>
          </form>
        </div>

        {/* Filtres */}
        {(modulsAmbLinks.length > 1 || cursos.length > 1) && (
          <div className="flex flex-wrap items-center gap-2">
            <select value={filtreModul} onChange={e => setFiltreModul(e.target.value)} aria-label="Filtra per mòdul"
              className="border rounded-lg px-2 py-1.5 text-sm max-w-xs">
              <option value="">Tots els mòduls</option>
              {modulsAmbLinks.map(i => <option key={i.modulId} value={i.modulId}>{i.modulCodi} — {i.modulNom}</option>)}
            </select>
            <select value={filtreCurs} onChange={e => setFiltreCurs(e.target.value)} aria-label="Filtra per curs"
              className="border rounded-lg px-2 py-1.5 text-sm">
              <option value="">Tots els cursos</option>
              {cursos.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
            {filtresActius && (
              <>
                <span className="text-xs text-gray-500">{filtrades.length} de {invitacions.length}</span>
                <button onClick={() => { setFiltreModul(''); setFiltreCurs('') }}
                  className="text-xs text-brand-600 hover:underline">Treu els filtres</button>
              </>
            )}
          </div>
        )}

        {/* Links actius */}
        <div className="space-y-2">
          <h2 className="text-sm font-medium text-gray-700">Links actius ({actives.length})</h2>
          {actives.length === 0 && (
            <p className="text-gray-400 text-sm">
              {filtresActius ? 'Cap link actiu amb aquests filtres.' : 'Encara no has generat cap link.'}
            </p>
          )}
          {actives.map(inv => (
            <InvitacioCard key={inv.id} inv={inv}
              onCopy={() => handleCopy(inv.token)}
              onDeactivate={() => handleDeactivate(inv)}
              copied={copied === inv.token}
              baseUrl={BASE_URL} />
          ))}
        </div>

        {/* Links caducats/desactivats */}
        {inactives.length > 0 && (
          <div className="space-y-2">
            <h2 className="text-sm font-medium text-gray-400">Historial ({inactives.length})</h2>
            {inactives.map(inv => (
              <InvitacioCard key={inv.id} inv={inv}
                onCopy={() => handleCopy(inv.token)}
                onDeactivate={() => {}}
                copied={copied === inv.token}
                baseUrl={BASE_URL}
                disabled />
            ))}
          </div>
        )}
      </div>
    </Layout>
  )
}

function InvitacioCard({ inv, onCopy, onDeactivate, copied, baseUrl, disabled = false }: {
  inv: Invitacio
  onCopy: () => void
  onDeactivate: () => void
  copied: boolean
  baseUrl: string
  disabled?: boolean
}) {
  const url = `${baseUrl}/invitacio/${inv.token}`
  const dies = daysLeft(inv.expiresAt)

  return (
    <div className={`bg-white border rounded-xl px-5 py-4 ${disabled ? 'opacity-50' : ''}`}>
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-mono text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded">
              {inv.modulCodi}
            </span>
            <span className="text-sm font-medium text-gray-800">{inv.modulNom}</span>
            <span className="text-xs text-gray-400">{inv.curs}</span>
            {inv.grupNom && (
              <span className="text-xs bg-brand-100 text-brand-700 px-2 py-0.5 rounded">
                Grup: {inv.grupNom}
              </span>
            )}
            {!disabled && dies <= 2 && (
              <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded">
                Caduca en {dies} dia{dies !== 1 ? 's' : ''}
              </span>
            )}
            {disabled && (
              <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded">
                {inv.active ? 'Caducat' : 'Desactivat'}
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 font-mono truncate max-w-sm">{url}</p>
          <p className="text-xs text-gray-400">{inv.usesCount} ús{inv.usesCount !== 1 ? 'os' : ''}</p>
        </div>
        <div className="flex gap-2 flex-shrink-0">
          <button onClick={onCopy}
            className={`text-xs rounded-lg px-3 py-1.5 border font-medium transition-colors ${
              copied
                ? 'bg-green-600 text-white border-green-600'
                : 'border-gray-300 text-gray-700 hover:bg-gray-50'
            }`}>
            {copied ? '✓ Copiat' : 'Copiar link'}
          </button>
          {!disabled && (
            <button onClick={onDeactivate}
              className="text-xs border border-red-200 rounded-lg px-3 py-1.5 hover:bg-red-50 text-red-600">
              Desactivar
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

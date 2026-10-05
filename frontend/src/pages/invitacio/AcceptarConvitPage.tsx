import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getInvitacioPublica, acceptarConvit, unirSeConvit } from '../../api/invitacions'
import { useAuth } from '../../context/AuthContext'
import type { InvitacioPublica } from '../../types'

export default function AcceptarConvitPage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const { user, setUser } = useAuth()

  const [info, setInfo]       = useState<InvitacioPublica | null>(null)
  const [loadError, setLoadError] = useState('')
  const [nom, setNom]         = useState('')
  const [email, setEmail]     = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [done, setDone]       = useState(false)

  useEffect(() => {
    if (!token) return
    getInvitacioPublica(token)
      .then(setInfo)
      .catch(() => setLoadError('Aquest link de convit no és vàlid o ha caducat.'))
  }, [token])

  // Alumne ja autenticat: mostra botó "Unir-me"
  const handleUnirSe = async () => {
    if (!token) return
    setSubmitting(true); setSubmitError('')
    try {
      await unirSeConvit(token)
      setDone(true)
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : 'Error en unir-se al mòdul')
    } finally { setSubmitting(false) }
  }

  // Alumne nou: registre + matrícula + auto-login
  const handleRegistre = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token) return
    setSubmitting(true); setSubmitError('')
    try {
      const resp = await acceptarConvit(token, nom, email, password)
      setUser({ userId: resp.userId, name: resp.name, email: resp.email, role: resp.role, token: resp.token })
      setDone(true)
    } catch (e: any) {
      setSubmitError(e?.response?.data?.error || (e instanceof Error ? e.message : 'Error en completar el registre'))
    } finally { setSubmitting(false) }
  }

  if (loadError) {
    return (
      <Screen>
        <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-center space-y-3">
          <p className="text-red-700 font-medium">Link no vàlid</p>
          <p className="text-sm text-red-600">{loadError}</p>
          <button onClick={() => navigate('/login')}
            className="text-brand-600 text-sm underline">Ves a l'inici de sessió</button>
        </div>
      </Screen>
    )
  }

  if (!info) {
    return <Screen><p className="text-gray-400 text-center py-8">Carregant…</p></Screen>
  }

  if (done) {
    return (
      <Screen>
        <div className="bg-green-50 border border-green-200 rounded-xl p-6 text-center space-y-4">
          <div className="text-4xl">✓</div>
          <p className="font-semibold text-green-800">Ja ets al mòdul!</p>
          <p className="text-sm text-green-700">
            Ara estàs matriculat a <strong>{info.modulCodi} — {info.modulNom}</strong> ({info.curs}).
          </p>
          <button onClick={() => navigate('/student/exams')}
            className="bg-brand-600 text-white rounded-lg px-5 py-2 text-sm hover:bg-brand-700">
            Veure els meus exàmens
          </button>
        </div>
      </Screen>
    )
  }

  return (
    <Screen>
      {/* Info del mòdul */}
      <div className="bg-brand-50 border border-brand-200 rounded-xl p-5 space-y-1 mb-6">
        <p className="text-xs text-brand-500 font-medium uppercase tracking-wide">Convit a</p>
        <p className="text-lg font-bold text-brand-800">
          <span className="font-mono">{info.modulCodi}</span> — {info.modulNom}
        </p>
        <p className="text-sm text-brand-600">{info.cicleNom} · {info.departamentNom}</p>
        <p className="text-xs text-brand-400">Curs {info.curs} · Convidat per {info.professorNom}</p>
      </div>

      {/* Cas: alumne ja autenticat */}
      {user ? (
        <div className="space-y-4 text-center">
          <p className="text-sm text-gray-600">
            Sessió activa com a <strong>{user.name}</strong>.
          </p>
          {submitError && (
            <p className="text-red-600 text-sm">{submitError}</p>
          )}
          <button onClick={handleUnirSe} disabled={submitting}
            className="w-full bg-brand-600 text-white rounded-xl py-3 font-medium hover:bg-brand-700 disabled:opacity-50">
            {submitting ? 'Processant…' : 'Unir-me al mòdul'}
          </button>
          <button onClick={() => { localStorage.removeItem('auth'); localStorage.removeItem('token'); window.location.reload() }}
            className="text-xs text-gray-400 underline">
            Accedir amb un altre compte
          </button>
        </div>
      ) : (
        /* Cas: alumne nou → formulari de registre */
        <form onSubmit={handleRegistre} className="space-y-4">
          <p className="text-sm text-gray-600 text-center">
            Crea el teu compte per accedir als exàmens d'aquesta assignatura.
          </p>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nom complet</label>
            <input type="text" value={nom} onChange={e => setNom(e.target.value)} required
              placeholder="p. ex. Maria García"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Correu electrònic</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
              placeholder="alumne@centre.cat"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Contrasenya <span className="text-gray-400">(mínim 8 caràcters)</span></label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)}
              required minLength={8}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
            <p className="text-xs text-gray-400 mt-1">Si ja tens compte a la plataforma, escriu la teva contrasenya actual.</p>
          </div>
          {submitError && (
            <p className="text-red-600 text-sm bg-red-50 rounded-lg px-3 py-2">{submitError}</p>
          )}
          <button type="submit" disabled={submitting || !nom || !email || password.length < 8}
            className="w-full bg-brand-600 text-white rounded-xl py-3 font-medium hover:bg-brand-700 disabled:opacity-50">
            {submitting ? 'Creant compte…' : 'Crear compte i unir-me'}
          </button>
          <p className="text-xs text-center text-gray-400">
            Ja tens compte?{' '}
            <button type="button" onClick={() => navigate('/login')}
              className="text-brand-600 underline">Inicia sessió primer</button>
          </p>
        </form>
      )}
    </Screen>
  )
}

function Screen({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md p-8">
        <div className="text-center mb-6">
          <h1 className="text-xl font-bold text-brand-700">SEDEX</h1>
          <p className="text-xs text-gray-400 mt-0.5">Sistema d'Entorn de Desenvolupament d'Exàmens</p>
        </div>
        {children}
      </div>
    </div>
  )
}

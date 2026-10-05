import { useState, FormEvent, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useConfiguracio } from '../context/ConfiguracioContext'
import { getLogoUrl } from '../api/configuracio'
import { login } from '../api/auth'

const OAUTH_ERRORS: Record<string, string> = {
  no_email:  'El compte de Google no té correu electrònic visible.',
  domain:    'Accés denegat: el domini del compte no està autoritzat.',
  disabled:  'El teu compte ha estat desactivat. Contacta amb un administrador.',
  oauth:     'Error durant el login amb Google. Torna-ho a intentar.',
}

const BACKEND_URL = import.meta.env.VITE_API_URL ?? ''

export default function LoginPage() {
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const { setUser }             = useAuth()
  const { config }              = useConfiguracio()
  const navigate                = useNavigate()
  const [searchParams]          = useSearchParams()
  const nomCentre = config?.nomCentre || "Plataforma d'Avaluació"

  useEffect(() => {
    const oauthError = searchParams.get('error')
    if (oauthError) setError(OAUTH_ERRORS[oauthError] ?? 'Error d\'autenticació.')
  }, [searchParams])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      const data = await login(email, password)
      setUser(data)
      navigate('/')
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Credencials incorrectes')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-brand-50 flex items-center justify-center">
      <div className="bg-white rounded-xl shadow-lg p-10 w-full max-w-sm">
        <div className="text-center mb-8">
          {config?.hasLogo ? (
            <img src={getLogoUrl()} alt={nomCentre} className="h-14 w-auto object-contain mx-auto mb-3" />
          ) : (
            <div className="inline-block bg-brand-600 text-white text-2xl font-bold px-4 py-2 rounded-lg mb-3">
              {nomCentre.slice(0, 2).toUpperCase()}
            </div>
          )}
          <h1 className="text-xl font-semibold text-brand-700">{nomCentre}</h1>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Correu electrònic</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Contrasenya</label>
            <input type="password" value={password} onChange={e => setPassword(e.target.value)} required
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-500" />
          </div>
          {error && <p className="text-red-600 text-sm">{error}</p>}
          <button type="submit" disabled={loading}
            className="w-full bg-brand-600 hover:bg-brand-700 text-white rounded-lg py-2 text-sm font-medium disabled:opacity-50">
            {loading ? 'Entrant...' : 'Entrar'}
          </button>
        </form>

        {/* Només si el servidor té Google configurat (amb accés per IP no pot funcionar) */}
        {config?.googleActiu && (<>
        <div className="mt-6 flex items-center gap-3">
          <span className="flex-1 border-t border-gray-200" />
          <span className="text-xs text-gray-400">o bé</span>
          <span className="flex-1 border-t border-gray-200" />
        </div>

        <a href={`${BACKEND_URL}/oauth2/authorization/google`}
           className="mt-4 flex items-center justify-center gap-3 border border-gray-300 rounded-lg py-2 px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
          <svg className="w-5 h-5" viewBox="0 0 48 48">
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
          </svg>
          Entrar amb Google
        </a>
        </>)}
      </div>
    </div>
  )
}

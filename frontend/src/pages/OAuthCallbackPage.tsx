import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { Role } from '../types'

/**
 * Pàgina de destí del redirect OAuth2.
 * El backend redirigeix a /oauth-callback#token=...&name=...&role=...&userId=...
 * Llegim el fragment (no va al servidor), desem l'usuari i redirigim.
 */
export default function OAuthCallbackPage() {
  const { setUser } = useAuth()
  const navigate = useNavigate()
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const fragment = window.location.hash.slice(1)
    const params = new URLSearchParams(fragment)

    const token  = params.get('token')
    const name   = params.get('name')
    const role   = params.get('role') as Role | null
    const userId = params.get('userId')
    const email  = params.get('email') ?? ''

    if (!token || !name || !role || !userId) {
      navigate('/login?error=oauth', { replace: true })
      return
    }

    setUser({ token, name, email, role, userId })

    if (role === 'ADMIN')      navigate('/admin/users', { replace: true })
    else if (role === 'PROFESSOR') navigate('/professor/exams', { replace: true })
    else navigate('/student/exams', { replace: true })
  }, [navigate, setUser])

  return (
    <div className="flex items-center justify-center h-screen bg-gray-50">
      <p className="text-gray-500 animate-pulse">Iniciant sessió…</p>
    </div>
  )
}

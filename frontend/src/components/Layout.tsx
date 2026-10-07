import { useEffect, useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useConfiguracio } from '../context/ConfiguracioContext'
import { getCopiesSeguretat } from '../api/configuracio'
import { getLogoUrl } from '../api/configuracio'

const NAV: { to: string; label: string; roles: string[] }[] = [
  { to: '/professor/exams',    label: 'Examens',              roles: ['PROFESSOR', 'ADMIN'] },
  { to: '/professor/grups',    label: 'Grups i alumnes',      roles: ['PROFESSOR', 'ADMIN'] },
  { to: '/professor/convits',  label: 'Links de convit',      roles: ['PROFESSOR', 'ADMIN'] },
  { to: '/admin/users',        label: 'Gestió d\'usuaris',    roles: ['ADMIN', 'PROFESSOR'] },
  { to: '/admin/estructura',   label: 'Estructura acadèmica', roles: ['ADMIN'] },
  { to: '/admin/matricules',   label: 'Matrícules',           roles: ['ADMIN'] },
  { to: '/admin/aules',        label: 'Aules',                roles: ['ADMIN'] },
  { to: '/admin/configuracio', label: 'Configuració',         roles: ['ADMIN'] },
  { to: '/admin/emmagatzematge', label: 'Espai ocupat',       roles: ['ADMIN'] },
  { to: '/admin/audit',        label: 'Registre activitat',   roles: ['ADMIN'] },
  { to: '/student/exams',      label: 'Els meus examens',     roles: ['STUDENT'] },
  { to: '/student/historial',  label: 'Historial i notes',    roles: ['STUDENT'] },
]

interface LayoutProps {
  children: React.ReactNode
  examMode?: boolean
}

export default function Layout({ children, examMode = false }: LayoutProps) {
  const { user, logout } = useAuth()
  const { config } = useConfiguracio()
  const [avisCopies, setAvisCopies] = useState<string | null>(null)

  // Els administradors veuen a totes les pàgines si les còpies de seguretat fallen
  useEffect(() => {
    if (user?.role !== 'ADMIN') return
    getCopiesSeguretat()
      .then(c => setAvisCopies(c.alerta ? (c.motiuAlerta ?? 'Problema amb les còpies de seguretat') : null))
      .catch(() => { /* sense permís o sense connexió: no avisem */ })
  }, [user?.role])
  const navigate = useNavigate()
  const location = useLocation()

  const links = NAV.filter(l => user && l.roles.includes(user.role))
  const nomCentre = config?.nomCentre || "Plataforma d'Avaluació"

  if (examMode) {
    return (
      <div className="min-h-screen bg-gray-50">
        <header className="bg-brand-700 text-white px-6 py-3 flex items-center justify-between shadow">
          <span className="font-semibold text-sm">{nomCentre}</span>
          <div className="flex items-center gap-4">
            <span className="text-brand-300 text-xs">{user?.name}</span>
            <button onClick={() => { logout(); navigate('/login') }}
              className="text-xs bg-brand-600 hover:bg-brand-500 text-brand-200 hover:text-white rounded px-2 py-1 transition-colors">
              Tancar sessió
            </button>
          </div>
        </header>
        <main className="max-w-3xl mx-auto px-6 py-8">{children}</main>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex bg-gray-50">
      {/* Sidebar */}
      <aside className="w-56 bg-brand-700 text-white flex flex-col fixed h-full z-10 shadow-lg print:hidden">
        <div className="px-5 py-5 border-b border-brand-600">
          {config?.hasLogo ? (
            <img src={getLogoUrl()} alt={nomCentre} className="h-8 w-auto object-contain mb-1" />
          ) : (
            <div className="font-bold text-base leading-tight">{nomCentre}</div>
          )}
          {config?.cursActiu && (
            <div className="text-brand-300 text-xs mt-0.5">{config.cursActiu}</div>
          )}
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {links.map(l => {
            const active = location.pathname.startsWith(l.to)
            return (
              <Link key={l.to} to={l.to}
                className={`flex items-center px-3 py-2 rounded-lg text-sm transition-colors ${
                  active
                    ? 'bg-brand-500 text-white font-medium'
                    : 'text-brand-200 hover:bg-brand-600 hover:text-white'
                }`}>
                {l.label}
              </Link>
            )
          })}
        </nav>

        <div className="px-4 py-4 border-t border-brand-600 space-y-2">
          <p className="text-xs text-brand-200 font-medium truncate">{user?.name}</p>
          <p className="text-xs text-brand-400 truncate">{user?.email}</p>
          <p className="text-xs text-brand-500 capitalize">{user?.role?.toLowerCase()}</p>
          <button onClick={() => { logout(); navigate('/login') }}
            className="w-full text-xs bg-brand-600 hover:bg-brand-500 text-brand-200 hover:text-white rounded px-2 py-1.5 transition-colors">
            Tancar sessió
          </button>
        </div>
      </aside>

      {/* Contingut principal */}
      <div className="flex-1 ml-56 print:ml-0">
        {avisCopies && (
          <div role="alert" className="bg-red-600 text-white text-sm px-6 py-2 flex flex-wrap items-center justify-between gap-2 print:hidden">
            <span>⚠ <strong>Còpies de seguretat:</strong> {avisCopies}</span>
            <Link to="/admin/configuracio" className="underline text-white/90 hover:text-white">Veure l'estat</Link>
          </div>
        )}
        <main className="max-w-5xl mx-auto px-6 py-8">{children}</main>
      </div>
    </div>
  )
}

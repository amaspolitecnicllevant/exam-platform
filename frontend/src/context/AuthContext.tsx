import { createContext, useContext, useState, ReactNode } from 'react'
import type { Role } from '../types'

interface AuthUser { userId: string; name: string; email: string; role: Role; token: string }
interface AuthContextType {
  user: AuthUser | null
  setUser: (u: AuthUser | null) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextType>(null!)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<AuthUser | null>(() => {
    const raw = localStorage.getItem('auth')
    return raw ? JSON.parse(raw) : null
  })

  const setUser = (u: AuthUser | null) => {
    setUserState(u)
    if (u) {
      localStorage.setItem('auth', JSON.stringify(u))
      localStorage.setItem('token', u.token)
    } else {
      localStorage.removeItem('auth')
      localStorage.removeItem('token')
    }
  }

  const logout = () => setUser(null)

  return <AuthContext.Provider value={{ user, setUser, logout }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

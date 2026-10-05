import axios from 'axios'

const client = axios.create({ baseURL: '/api' })

/** Quan al token li queda menys d'això, es renova (perquè no caduqui a mig examen). */
const MARGE_RENOVACIO_MS = 60 * 60_000
let renovant: Promise<void> | null = null

/** Moment de caducitat (ms) del JWT, o null si no es pot llegir. */
function caducitat(token: string): number | null {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const exp = JSON.parse(atob(b64.padEnd(b64.length + (4 - b64.length % 4) % 4, '='))).exp
    return typeof exp === 'number' ? exp * 1000 : null
  } catch { return null }
}

/** Renova el token si li queda poc. Amb axios directe, per no passar pels interceptors. */
export function renovaTokenSiCal(): void {
  const token = localStorage.getItem('token')
  if (!token || renovant) return
  const exp = caducitat(token)
  if (exp == null) return
  const queda = exp - Date.now()
  if (queda <= 0 || queda > MARGE_RENOVACIO_MS) return
  renovant = axios.post<{ token: string }>('/api/auth/refresh', null, { headers: { Authorization: `Bearer ${token}` } })
    .then(r => {
      localStorage.setItem('token', r.data.token)
      try {
        const auth = JSON.parse(localStorage.getItem('auth') ?? 'null')
        if (auth) localStorage.setItem('auth', JSON.stringify({ ...auth, token: r.data.token }))
      } catch { /* sense emmagatzematge */ }
    })
    .catch(() => { /* si falla, el token caducarà com abans */ })
    .finally(() => { renovant = null })
}

// També sense peticions (p. ex. un alumne llegint l'enunciat molta estona)
setInterval(renovaTokenSiCal, 5 * 60_000)

client.interceptors.request.use(config => {
  renovaTokenSiCal()
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
  r => r,
  err => {
    // Un 401 del login o d'un convit és "contrasenya incorrecta", no una sessió caducada
    const url: string = err.config?.url ?? ''
    const esCredencials = url.startsWith('/auth/login') || url.includes('/invitacions/publica/')
    if (err.response?.status === 401 && !esCredencials) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default client

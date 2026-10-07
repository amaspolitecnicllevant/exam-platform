import { useState } from 'react'
import { canviaLaMevaContrasenya } from '../api/users'

/** Finestra perquè l'usuari canviï la seva pròpia contrasenya (cal l'actual). */
export default function CanviarContrasenya({ onClose }: { onClose: () => void }) {
  const [actual, setActual] = useState('')
  const [nova, setNova] = useState('')
  const [repeteix, setRepeteix] = useState('')
  const [error, setError] = useState('')
  const [fet, setFet] = useState(false)
  const [enviant, setEnviant] = useState(false)

  async function envia(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (nova.length < 8 || nova.length > 72) return setError('La contrasenya nova ha de tenir entre 8 i 72 caràcters')
    if (nova !== repeteix) return setError('Les dues contrasenyes noves no coincideixen')
    setEnviant(true)
    try {
      await canviaLaMevaContrasenya(actual, nova)
      setFet(true)
    } catch (err: any) {
      const d = err?.response?.data
      setError(d?.error || d?.message || 'No s\'ha pogut canviar la contrasenya')
    } finally {
      setEnviant(false)
    }
  }

  const camp = 'w-full border border-gray-300 rounded px-3 py-2 text-sm text-gray-900'
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl w-full max-w-sm p-6 text-gray-900">
        <h2 className="text-lg font-semibold mb-4">Canviar la contrasenya</h2>
        {fet ? (
          <>
            <p className="text-sm text-green-700 mb-4">Contrasenya canviada correctament.</p>
            <button onClick={onClose} className="w-full bg-brand-600 text-white rounded px-3 py-2 text-sm">Tanca</button>
          </>
        ) : (
          <form onSubmit={envia} className="space-y-3">
            <label className="block text-sm">Contrasenya actual
              <input type="password" autoComplete="current-password" required value={actual}
                onChange={e => setActual(e.target.value)} className={camp} />
            </label>
            <label className="block text-sm">Contrasenya nova (mínim 8 caràcters)
              <input type="password" autoComplete="new-password" required value={nova}
                onChange={e => setNova(e.target.value)} className={camp} />
            </label>
            <label className="block text-sm">Repeteix la contrasenya nova
              <input type="password" autoComplete="new-password" required value={repeteix}
                onChange={e => setRepeteix(e.target.value)} className={camp} />
            </label>
            {error && <p role="alert" className="text-red-600 text-sm">{error}</p>}
            <div className="flex gap-2 pt-1">
              <button type="button" onClick={onClose}
                className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm">Cancel·la</button>
              <button type="submit" disabled={enviant}
                className="flex-1 bg-brand-600 text-white rounded px-3 py-2 text-sm disabled:opacity-50">
                {enviant ? 'Desant…' : 'Canvia-la'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

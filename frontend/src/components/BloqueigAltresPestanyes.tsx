import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { avisaIntrusa, examenEnAltraPestanya } from '../utils/unaPestanya'

/**
 * Per als alumnes: si hi ha un examen en curs en una altra pestanya, aquesta pestanya es bloqueja
 * (i l'incident es registra a la pestanya de l'examen). Es desbloqueja sola quan l'examen acaba.
 */
export default function BloqueigAltresPestanyes() {
  const { user } = useAuth()
  const [bloquejada, setBloquejada] = useState(false)
  const esAlumne = user?.role === 'STUDENT'

  useEffect(() => {
    if (!esAlumne) { setBloquejada(false); return }
    let viu = true
    let avisada = false
    const comprova = async () => {
      const ocupat = await examenEnAltraPestanya()
      if (!viu) return
      setBloquejada(ocupat)
      if (ocupat && !avisada) { avisada = true; avisaIntrusa() }
      if (!ocupat) avisada = false
    }
    comprova()
    const id = setInterval(comprova, 2000)
    return () => { viu = false; clearInterval(id) }
  }, [esAlumne])

  if (!bloquejada) return null
  return (
    <div role="alertdialog" aria-modal="true"
      className="fixed inset-0 z-50 bg-gray-900 flex items-center justify-center px-6 text-center">
      <div className="max-w-md text-white space-y-3">
        <div className="text-5xl">🚫</div>
        <h2 className="text-xl font-semibold">Hi ha un examen en curs en una altra pestanya</h2>
        <p className="text-gray-300 text-sm">
          Durant l'examen només es pot tenir oberta una pestanya. Tanca aquesta i torna a la de l'examen.
          Aquest intent ha quedat registrat.
        </p>
      </div>
    </div>
  )
}

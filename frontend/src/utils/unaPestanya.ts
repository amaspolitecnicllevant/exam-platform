/**
 * Control d'una sola pestanya durant un examen.
 *
 * La pestanya que fa l'examen agafa un pany (Web Locks) mentre és oberta. Qualsevol altra pestanya
 * de la plataforma —una segona còpia de l'examen o una altra pàgina— detecta el pany, es bloqueja
 * i ho notifica a la pestanya de l'examen, que ho registra com a incidència.
 *
 * Els Web Locks només existeixen en context segur (HTTPS o localhost). Sense ells el control no
 * es pot fer i es deixa passar (queda només la detecció de pèrdua de focus).
 */

const PANY = 'examen-actiu'
const CANAL = 'examen-pestanya'

export type EstatPestanya = 'propietaria' | 'duplicada' | 'no-suportat'

let sociPropietari = false

const suportat = () => typeof navigator !== 'undefined' && !!navigator.locks && typeof BroadcastChannel !== 'undefined'

function intentaAgafar(): Promise<{ estat: EstatPestanya; allibera: () => void }> {
  return new Promise(resolve => {
    let allibera = () => {}
    navigator.locks.request(PANY, { ifAvailable: true }, lock => {
      if (!lock) {
        resolve({ estat: 'duplicada', allibera })
        return undefined
      }
      sociPropietari = true
      resolve({ estat: 'propietaria', allibera: () => allibera() })
      return new Promise<void>(res => {
        allibera = () => { sociPropietari = false; res() }
      })
    })
  })
}

/**
 * Intenta ser l'única pestanya d'examen. Si el pany és ocupat, ho reintenta una vegada al cap d'un
 * moment (un pany alliberat just abans, p. ex. en recarregar la pàgina, tarda a quedar lliure).
 */
export async function agafaPestanyaExamen(): Promise<{ estat: EstatPestanya; allibera: () => void }> {
  if (!suportat()) return { estat: 'no-suportat', allibera: () => {} }
  const primer = await intentaAgafar()
  if (primer.estat === 'propietaria') return primer
  await new Promise(r => setTimeout(r, 700))
  return intentaAgafar()
}

/** Avisa la pestanya de l'examen que aquesta pestanya és una intrusa. */
export function avisaIntrusa(): void {
  if (!suportat()) return
  try {
    const c = new BroadcastChannel(CANAL)
    c.postMessage('intrusa')
    c.close()
  } catch { /* sense canal */ }
}

/** A la pestanya de l'examen: crida `alIntrusa` quan una altra pestanya s'hi intenta obrir. */
export function escoltaIntrusos(alIntrusa: () => void): () => void {
  if (!suportat()) return () => {}
  const c = new BroadcastChannel(CANAL)
  c.onmessage = e => { if (e.data === 'intrusa') alIntrusa() }
  return () => c.close()
}

/** Hi ha un examen en curs en una altra pestanya? */
export async function examenEnAltraPestanya(): Promise<boolean> {
  if (!suportat() || sociPropietari) return false
  try {
    const { held } = await navigator.locks.query()
    return !!held?.some(l => l.name === PANY)
  } catch { return false }
}

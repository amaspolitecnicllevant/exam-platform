export interface Disc {
  midaPreguntesDisc: number
  midaLliuramentsDisc: number
  espaiLliure: number | null
  espaiTotal: number | null
}

/** Marge per no avisar per diferències insignificants (metadades, fitxers que s'estan desant). */
export const TOLERANCIA_BYTES = 1024 * 1024

export type EstatCoherencia =
  | { tipus: 'ok' }
  /** Hi ha més fitxers al disc dels que la BD coneix: fitxers orfes que ocupen espai */
  | { tipus: 'sobra'; bytes: number }
  /** La BD diu que hi ha més fitxers dels que hi ha al disc: n'han desaparegut */
  | { tipus: 'falta'; bytes: number }

/** Compara l'espai que la BD diu que ocupen els fitxers amb el que hi ha realment al disc. */
export function coherenciaDisc(disc: Disc, totalSegonsBd: number, tolerancia = TOLERANCIA_BYTES): EstatCoherencia {
  const alDisc = disc.midaPreguntesDisc + disc.midaLliuramentsDisc
  const diferencia = alDisc - totalSegonsBd
  if (diferencia > tolerancia) return { tipus: 'sobra', bytes: diferencia }
  if (-diferencia > tolerancia) return { tipus: 'falta', bytes: -diferencia }
  return { tipus: 'ok' }
}

/** Percentatge (0–100) d'ús de la partició, o null si no es coneix. */
export function percentUs(disc: Disc): number | null {
  if (disc.espaiTotal == null || disc.espaiLliure == null || disc.espaiTotal <= 0) return null
  return Math.min(100, Math.max(0, Math.round(((disc.espaiTotal - disc.espaiLliure) / disc.espaiTotal) * 100)))
}

/** Amplada (0–100) de la barra d'una fila respecte de la més gran. */
export function ampladaBarra(valor: number, maxim: number): number {
  if (maxim <= 0 || valor <= 0) return 0
  return Math.max(2, Math.round((valor / maxim) * 100))
}

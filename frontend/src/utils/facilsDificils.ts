import type { PreguntaStats } from '../types'

export interface ExtremsRendiment {
  /** Les que han anat millor (rendiment més alt primer) */
  facils: PreguntaStats[]
  /** Les que han anat pitjor (rendiment més baix primer) */
  dificils: PreguntaStats[]
}

/**
 * Les preguntes que semblen més fàcils (més punts aconseguits sobre el màxim) i les més difícils.
 * No compten les de bonus ni les que encara no tenen dades. Mai hi ha una pregunta a les dues llistes:
 * amb poques preguntes, cada llista en té la meitat com a màxim.
 */
export function extremsRendiment(preguntes: PreguntaStats[], max = 5): ExtremsRendiment {
  const amb = preguntes.filter(p => !p.bonus && p.percentRendiment != null)
  if (amb.length < 2) return { facils: [], dificils: [] }
  const n = Math.min(max, Math.floor(amb.length / 2))
  const perRendiment = (a: PreguntaStats, b: PreguntaStats) =>
    (a.percentRendiment as number) - (b.percentRendiment as number) || a.ordre - b.ordre
  const ordenades = [...amb].sort(perRendiment)
  return { dificils: ordenades.slice(0, n), facils: ordenades.slice(-n).reverse() }
}

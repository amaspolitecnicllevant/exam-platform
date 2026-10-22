import type { Exam } from '../types'

/**
 * Competències que es proposen a les preguntes d'un examen: les que declara l'examen. {@code null} = text lliure
 * (l'examen no n'ha declarat cap). Només és una proposta: un codi diferent no es rebutja, només genera un avís.
 */
export function competenciesPermeses(exam: Pick<Exam, 'competenciesExamen'>): string[] | null {
  return exam.competenciesExamen && exam.competenciesExamen.length > 0 ? exam.competenciesExamen : null
}

export interface ResumCompetencia { codi: string; preguntes: number; punts: number }

/**
 * Competències que avalua l'examen amb el nombre de preguntes i punts de cadascuna: les que declara l'examen
 * més les que apareixen a les preguntes. Les declarades sense cap pregunta surten amb 0.
 */
export function resumCompetencies(exam: Pick<Exam, 'competenciesExamen' | 'questions'>): ResumCompetencia[] {
  const mapa = new Map<string, ResumCompetencia>()
  for (const codi of exam.competenciesExamen ?? []) mapa.set(codi, { codi, preguntes: 0, punts: 0 })
  for (const q of exam.questions) {
    if (q.tipus === 'SECTION' || !q.competencia) continue
    const r = mapa.get(q.competencia) ?? { codi: q.competencia, preguntes: 0, punts: 0 }
    r.preguntes++
    r.punts += q.punts
    mapa.set(q.competencia, r)
  }
  return [...mapa.values()].sort((a, b) => a.codi.length - b.codi.length || a.codi.localeCompare(b.codi))
}

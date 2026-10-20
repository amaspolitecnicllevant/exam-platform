import type { Exam } from '../types'

/**
 * Competències que es proposen a les preguntes d'un examen: les que declara l'examen. {@code null} = text lliure
 * (l'examen no n'ha declarat cap). Només és una proposta: un codi diferent no es rebutja, només genera un avís.
 */
export function competenciesPermeses(exam: Pick<Exam, 'competenciesExamen'>): string[] | null {
  return exam.competenciesExamen && exam.competenciesExamen.length > 0 ? exam.competenciesExamen : null
}

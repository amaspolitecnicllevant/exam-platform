import type { CompetenciaProg, Exam } from '../types'

/**
 * Competències que es proposen per a les preguntes d'un examen: les que declara l'examen; si no n'ha declarat, les de
 * les UT que avalua; si no té UT, les del mòdul. {@code null} = text lliure (l'examen no té mòdul o el mòdul encara
 * no té programació). Ha de coincidir amb {@code CompetenciesExamen.permeses} del backend. Només és una proposta:
 * un codi que no sigui a la programació no es rebutja, només genera un avís.
 */
export function competenciesPermeses(
  exam: Pick<Exam, 'unitats' | 'competencies'> & { competenciesExamen?: string[] },
): CompetenciaProg[] | null {
  if (exam.competenciesExamen && exam.competenciesExamen.length > 0) {
    return exam.competenciesExamen.map(codi =>
      exam.competencies.find(c => c.codi === codi) ?? { id: codi, codi, descripcio: '' })
  }
  if (exam.unitats.length > 0) {
    const codis = new Set(exam.unitats.flatMap(u => u.competencies))
    return exam.competencies.filter(c => codis.has(c.codi))
  }
  return exam.competencies.length > 0 ? exam.competencies : null
}

/** «CO1 — Instal·la sistemes» (o només el codi si no hi ha descripció). */
export function etiquetaCompetencia(c: CompetenciaProg): string {
  return c.descripcio ? `${c.codi} — ${c.descripcio}` : c.codi
}

/** Descripció d'un codi segons les competències del mòdul (per a «title» als badges). */
export function descripcioCompetencia(exam: Pick<Exam, 'competencies'>, codi: string | undefined): string | undefined {
  if (!codi) return undefined
  return exam.competencies.find(c => c.codi === codi)?.descripcio || undefined
}

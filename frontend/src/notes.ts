import type { Answer, Question } from './types'

/**
 * Punts que compten per a una pregunta (la mateixa regla que Puntuacio.java al backend):
 * - bonus: els punts sencers per a tothom, hagi respost o no;
 * - altrament, la nota revisada o, si no n'hi ha, la proposta automàtica;
 * - sense resposta: 0.
 * Retorna null si hi ha resposta però encara no té cap nota.
 */
export function puntsPregunta(q: Question, a: Answer | undefined): number | null {
  if (q.tipus === 'SECTION') return 0
  if (q.anulada) return q.punts
  if (!a) return 0
  return a.manualScore ?? a.autoScore ?? null
}

/** Nota total d'un alumne. Les respostes sense nota compten 0 i es marquen com a pendents. */
export function notaTotal(questions: Question[], answers: Answer[]): { total: number; pendents: number } {
  const perPregunta = new Map(answers.map(a => [a.questionId, a]))
  let total = 0, pendents = 0
  for (const q of questions) {
    if (q.tipus === 'SECTION') continue
    const p = puntsPregunta(q, perPregunta.get(q.id))
    if (p == null) pendents++
    else total += p
  }
  return { total, pendents }
}

/** Punts màxims de l'examen (sense les seccions). */
export function puntsMaxims(questions: Question[]): number {
  return questions.filter(q => q.tipus !== 'SECTION').reduce((s, q) => s + q.punts, 0)
}

/** Nota sobre 10 (la mateixa regla que Puntuacio.sobreDeu al backend). Null si l'examen no té punts. */
export function sobreDeu(punts: number, maxim: number): number | null {
  return maxim > 0 ? Math.round(punts * 1000 / maxim) / 100 : null
}

/** Nota a partir de la qual s'aprova (sobre 10). */
export const NOTA_APROVAT = 5

/** Nota sobre 10 d'una competència a partir dels seus punts; null si no té punts. */
export function notaCompetenciaSobreDeu(g: { pts: number; scored: number }): number | null {
  return sobreDeu(g.scored, g.pts)
}

/** Nota per competència (inclou les preguntes amb bonus). */
export function notaPerCompetencia(questions: Question[], answers: Answer[]): [string, { pts: number; scored: number }][] {
  const perPregunta = new Map(answers.map(a => [a.questionId, a]))
  const grups: Record<string, { pts: number; scored: number }> = {}
  for (const q of questions) {
    if (!q.competencia || q.tipus === 'SECTION') continue
    grups[q.competencia] ??= { pts: 0, scored: 0 }
    grups[q.competencia].pts += q.punts
    grups[q.competencia].scored += puntsPregunta(q, perPregunta.get(q.id)) ?? 0
  }
  return Object.entries(grups).sort(([a], [b]) => a.localeCompare(b))
}

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
  if (q.anulada) return maximPregunta(q)
  if (!a) return 0
  const brut = a.manualScore ?? a.autoScore ?? null
  return brut == null ? null : brut * (q.factor ?? 1)
}

/**
 * Punts màxims d'una pregunta amb el pes de la seva part aplicat (el mateix que Puntuacio.maximPregunta).
 * Les notes de les respostes estan sempre sobre els punts «bruts» (`q.punts`); això només és per als totals.
 */
export function maximPregunta(q: Question): number {
  return q.punts * (q.factor ?? 1)
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
  return questions.filter(q => q.tipus !== 'SECTION').reduce((s, q) => s + maximPregunta(q), 0)
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
    grups[q.competencia].pts += maximPregunta(q)
    grups[q.competencia].scored += puntsPregunta(q, perPregunta.get(q.id)) ?? 0
  }
  return Object.entries(grups).sort(([a], [b]) => a.localeCompare(b))
}

/** Una part de l'examen (secció «###»): clau estable per a columnes i punts màxims. */
export interface PartExamen { clau: string; titol: string; preguntes: Question[] }

/**
 * Parts de l'examen en ordre: cada secció amb les preguntes que té fins a la següent. Les preguntes anteriors a la
 * primera secció formen «Sense secció». Buit si l'examen no té cap secció (mateixa regla que StatsService.perPart).
 */
export function partsExamen(questions: Question[]): PartExamen[] {
  if (!questions.some(q => q.tipus === 'SECTION')) return []
  const parts: PartExamen[] = []
  let actual: PartExamen = { clau: '#0', titol: 'Sense secció', preguntes: [] }
  parts.push(actual)
  for (const q of [...questions].sort((a, b) => a.ordre - b.ordre)) {
    if (q.tipus === 'SECTION') {
      actual = { clau: `#${parts.length}`, titol: q.enunciat, preguntes: [] }
      parts.push(actual)
    } else {
      actual.preguntes.push(q)
    }
  }
  return parts.filter(p => p.preguntes.length > 0)
}

/** Punts reals i màxims d'un alumne en un grup de preguntes. */
export function puntsGrup(preguntes: Question[], answers: Answer[]): { pts: number; scored: number } {
  const perPregunta = new Map(answers.map(a => [a.questionId, a]))
  return {
    pts: preguntes.reduce((s, q) => s + maximPregunta(q), 0),
    scored: preguntes.reduce((s, q) => s + (puntsPregunta(q, perPregunta.get(q.id)) ?? 0), 0),
  }
}

/**
 * Nota sobre 10 que tindria l'alumne amb els valors inicials de l'examen: sense pesos per part i amb la penalització
 * del test original. Les notes manuals i les de les preguntes que no són de test es mantenen tal com estan.
 */
export function notaOriginal(questions: Question[], answers: Answer[], penalitzacioOriginal: number): number | null {
  const perPregunta = new Map(answers.map(a => [a.questionId, a]))
  let total = 0, maxim = 0
  for (const q of questions) {
    if (q.tipus === 'SECTION') continue
    maxim += q.punts
    const a = perPregunta.get(q.id)
    if (q.anulada) { total += q.punts; continue }
    if (!a) continue
    if (a.manualScore != null) { total += a.manualScore; continue }
    if (q.tipus === 'CHOICE') {
      const donada = (a.contingut ?? '').trim().toLowerCase()
      if (!donada) continue
      total += donada === (q.correctChoice ?? '').trim().toLowerCase() ? q.punts : -q.punts * penalitzacioOriginal
    } else {
      total += a.autoScore ?? 0
    }
  }
  return sobreDeu(total, maxim)
}

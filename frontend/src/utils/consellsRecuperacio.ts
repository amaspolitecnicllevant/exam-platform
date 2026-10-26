import type { Answer, Question, QuestionType } from '../types'
import { NOTA_APROVAT, notaCompetenciaSobreDeu, notaPerCompetencia, notaTotal, puntsMaxims, puntsPregunta, sobreDeu } from '../notes'

export interface ConsellsRecuperacio {
  /** Consells per a tot l'examen; null si l'alumne l'ha aprovat (o encara hi ha notes pendents). */
  examen: { nota: number; fallades: number[] } | null
  /** Una entrada per cada competència suspesa amb nota definitiva. */
  /** `provisional`: encara hi ha preguntes d'aquesta competència pendents de correcció. */
  competencies: { codi: string; nota: number; provisional: boolean; fallades: number[]; consells: string[] }[]
}

export const CONSELL_PER_TIPUS: Partial<Record<QuestionType, string>> = {
  TEXT: 'repassa els conceptes i prova d\'explicar-los amb les teves paraules, sense mirar els apunts',
  SHORT: 'repassa els conceptes i prova d\'explicar-los amb les teves paraules, sense mirar els apunts',
  LONG: 'practica redactant respostes completes: defineix, explica i posa un exemple',
  CHOICE: 'quan fallis una opció, esbrina per què les altres són incorrectes; no n\'hi ha prou amb memoritzar la correcta',
  BASH_CMD: 'practica les ordres a un terminal fins que et surtin sense consultar-les',
  PS_CMD: 'practica les ordres a un terminal fins que et surtin sense consultar-les',
  BASH_SCRIPT: 'escriu scripts des de zero i executa\'ls; llegeix el missatge d\'error abans de canviar res',
  PS_SCRIPT: 'escriu scripts des de zero i executa\'ls; llegeix el missatge d\'error abans de canviar res',
  JAVA_PROG: 'programa exercicis similars sense copiar i compila\'ls tu mateix; revisa els errors de compilació',
  HTML_CSS: 'reprodueix pàgines petites des de zero i comprova-les al navegador',
  FILE_UPLOAD: 'repassa l\'enunciat del lliurament i els comentaris del professor, i reprodueix el treball pas a pas',
}

const mitja = (a: number[]) => a.map(n => `P${n}`).join(', ')

/** Preguntes (números visibles) on l'alumne no ha tret tots els punts. */
function fallades(preguntes: Question[], perPregunta: Map<string, Answer>): Question[] {
  return preguntes.filter(q => {
    const p = puntsPregunta(q, perPregunta.get(q.id))
    return p != null && p < q.punts
  })
}

/** Consells d'estudi per a la recuperació, a partir de les notes (regles fixes, sense IA). */
export function consellsRecuperacio(questions: Question[], answers: Answer[]): ConsellsRecuperacio {
  const perPregunta = new Map(answers.map(a => [a.questionId, a]))
  const reals = questions.filter(q => q.tipus !== 'SECTION')
  const { total, pendents } = notaTotal(questions, answers)
  const nota = pendents === 0 ? sobreDeu(total, puntsMaxims(questions)) : null

  const examen = nota != null && nota < NOTA_APROVAT
    ? { nota, fallades: fallades(reals, perPregunta).map(q => q.numero ?? 0) }
    : null

  const competencies = notaPerCompetencia(questions, answers).flatMap(([codi, g]) => {
    const n = notaCompetenciaSobreDeu(g)
    if (n == null || n >= NOTA_APROVAT) return []
    // Amb preguntes pendents la nota només és provisional (les pendents compten 0): els consells valen per a la part ja corregida
    const provisional = reals.some(q => q.competencia === codi && puntsPregunta(q, perPregunta.get(q.id)) == null)
    const qs = fallades(reals.filter(q => q.competencia === codi), perPregunta)
    const consells = [...new Set(qs.map(q => CONSELL_PER_TIPUS[q.tipus]).filter((c): c is string => !!c))]
    return [{ codi, nota: n, provisional, fallades: qs.map(q => q.numero ?? 0), consells }]
  })
  return { examen, competencies }
}

export const llistaPreguntes = mitja

/** Consells (sense repetits) segons els tipus de pregunta on s'ha fallat, amb la primera lletra en majúscula. */
export function consellsPerTipus(tipus: QuestionType[]): string[] {
  return [...new Set(tipus.map(t => CONSELL_PER_TIPUS[t]).filter((c): c is string => !!c))]
    .map(t => t.charAt(0).toUpperCase() + t.slice(1) + '.')
}

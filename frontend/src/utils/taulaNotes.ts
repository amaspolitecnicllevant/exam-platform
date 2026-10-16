import type { Exam, Session } from '../types'
import { notaPerCompetencia, notaCompetenciaSobreDeu, notaTotal, puntsMaxims, sobreDeu, NOTA_APROVAT } from '../notes'

export { NOTA_APROVAT }

export interface FilaNotes {
  sessio: Session
  entregat: boolean
  /** Estat en paraules: Entregat, En curs o No presentat */
  estat: string
  /** Nota de l'examen sobre 10; null si l'alumne no l'ha començat */
  nota: number | null
  /** Nota sobre 10 de cada competència (per codi); null si cap pregunta seva no té punts o no l'ha començat */
  competencies: Record<string, number | null>
  /** Respostes encara sense nota (compten 0 mentrestant) */
  pendents: number
}

export interface TaulaNotes {
  /** Codis de competència (declarades per l'examen o presents a les preguntes), en ordre natural */
  competencies: string[]
  files: FilaNotes[]
}

const collator = new Intl.Collator('ca', { numeric: true, sensitivity: 'base' })

/** Una fila per sessió: nota de l'examen i de cada competència sobre 10 (mateixa regla que el backend). */
export function taulaNotes(exam: Pick<Exam, 'questions' | 'competenciesExamen'>, sessions: Session[]): TaulaNotes {
  const codis = new Set<string>((exam.competenciesExamen ?? []).map(c => c.trim()).filter(Boolean))
  for (const q of exam.questions) if (q.tipus !== 'SECTION' && q.competencia?.trim()) codis.add(q.competencia.trim())
  const competencies = [...codis].sort((a, b) => collator.compare(a, b))
  const maxim = puntsMaxims(exam.questions)

  const files = sessions.map<FilaNotes>(sessio => {
    const entregat = sessio.status === 'SUBMITTED'
    const comencat = entregat || !!sessio.startedAt
    const { total, pendents } = notaTotal(exam.questions, sessio.answers)
    const perCompetencia = new Map(notaPerCompetencia(exam.questions, sessio.answers))
    const notes: Record<string, number | null> = {}
    for (const co of competencies) {
      const g = perCompetencia.get(co)
      notes[co] = comencat && g ? notaCompetenciaSobreDeu(g) : null
    }
    return {
      sessio, entregat,
      estat: entregat ? 'Entregat' : comencat ? 'En curs' : 'No presentat',
      nota: comencat ? sobreDeu(total, maxim) : null,
      competencies: notes,
      pendents: comencat ? pendents : 0,
    }
  })
  return { competencies, files }
}

/** Nota amb coma decimal i dues xifres («7,50»), com espera un Excel en català; buit si no n'hi ha. */
const num = (n: number | null) => n == null ? '' : n.toFixed(2).replace('.', ',')

/** Evita la injecció de fórmules (=, +, -, @) en obrir el CSV a Excel. */
const cel = (v: string) => {
  const t = v.trim()
  return t && '=+-@\t\r'.includes(t[0]) ? `'${t}` : t
}

const camp = (v: string) => /[;"\r\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v

/** CSV del que es veu a la taula (mateix ordre i mateixes files): separador «;», coma decimal i BOM UTF-8. */
export function csvTaulaNotes(competencies: string[], files: FilaNotes[]): string {
  const cap = ['alumne', 'estat', 'nota_sobre_10', ...competencies, 'pendents_de_revisar']
  const linies = [cap, ...files.map(f => [
    cel(f.sessio.studentName), f.estat, num(f.nota), ...competencies.map(co => num(f.competencies[co])),
    f.estat === 'No presentat' ? '' : String(f.pendents),
  ])]
  return '﻿' + linies.map(l => l.map(camp).join(';')).join('\r\n') + '\r\n'
}

export type ColumnaOrdre = 'alumne' | 'nota' | 'pendents' | string

/** Ordena per alumne (alfabètic) o per una nota (de menor a major; les files sense nota, al final). */
export function ordenaFiles(files: FilaNotes[], per: ColumnaOrdre, descendent: boolean): FilaNotes[] {
  const valor = (f: FilaNotes): number | null =>
    per === 'nota' ? f.nota : per === 'pendents' ? f.pendents : f.competencies[per] ?? null
  const dir = descendent ? -1 : 1
  return [...files].sort((a, b) => {
    if (per === 'alumne') return dir * collator.compare(a.sessio.studentName, b.sessio.studentName)
    const x = valor(a), y = valor(b)
    if (x == null && y == null) return collator.compare(a.sessio.studentName, b.sessio.studentName)
    if (x == null) return 1
    if (y == null) return -1
    return x === y ? collator.compare(a.sessio.studentName, b.sessio.studentName) : dir * (x - y)
  })
}

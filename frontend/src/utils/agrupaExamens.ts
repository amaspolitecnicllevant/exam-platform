import type { Exam } from '../types'

/** Com es mostra el llistat d'exàmens: seguit o agrupat per departament, cicle o mòdul. */
export type AgrupacioExamens = 'cap' | 'departament' | 'cicle' | 'modul'

export interface GrupExamens {
  clau: string
  /** Capçalera del grup (buit si no s'agrupa) */
  titol: string
  /** Context: p. ex. el cicle i el departament d'un mòdul */
  context: string
  exams: Exam[]
}

const SENSE: Record<Exclude<AgrupacioExamens, 'cap'>, string> = {
  departament: 'Sense departament',
  cicle: 'Sense cicle',
  modul: 'Sense mòdul assignat',
}

const collator = new Intl.Collator('ca')

/** Agrupa els exàmens (ja filtrats) i ordena els grups pel nom; els «sense …» van al final. */
export function agrupaExamens(exams: Exam[], per: AgrupacioExamens): GrupExamens[] {
  if (per === 'cap') return [{ clau: '', titol: '', context: '', exams }]
  const grups = new Map<string, GrupExamens>()
  for (const e of exams) {
    const nom = per === 'departament' ? e.departamentNom : per === 'cicle' ? e.cicleNom : e.modulNom
    const clau = nom ?? ''
    let g = grups.get(clau)
    if (!g) {
      const context = !nom ? ''
        : per === 'modul' ? [e.cicleNom, e.departamentNom].filter(Boolean).join(' · ')
        : per === 'cicle' ? (e.departamentNom ?? '')
        : ''
      g = { clau, titol: nom ?? SENSE[per], context, exams: [] }
      grups.set(clau, g)
    }
    g.exams.push(e)
  }
  return [...grups.values()].sort((a, b) =>
    a.clau === '' ? 1 : b.clau === '' ? -1 : collator.compare(a.titol, b.titol))
}

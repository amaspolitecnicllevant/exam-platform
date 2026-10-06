import type { Exam } from '../types'
import { dataLocal } from '../dates'
import { normalitza } from './filtreAlumnes'

/** Valor especial del filtre de mòdul: exàmens que encara no tenen mòdul. */
export const SENSE_MODUL = '__sense__'

/** Filtres de la llista d'exàmens. Un camp buit vol dir «sense filtre». */
export interface FiltreExamens {
  cerca: string
  /** Estat visual: draft, scheduled, active o closed */
  estat: string
  modul: string
  /** Nom del professor que l'ha creat */
  autor: string
  cicle: string
  /** Id del grup per al qual està programat */
  grup: string
  /** Data (AAAA-MM-DD, hora local) des de la qual es mostren; inclosa */
  dataDes: string
  /** Data (AAAA-MM-DD, hora local) fins a la qual es mostren; inclosa */
  dataFins: string
}

export const FILTRE_EXAMENS_BUIT: FiltreExamens = {
  cerca: '', estat: '', modul: '', autor: '', cicle: '', grup: '', dataDes: '', dataFins: '',
}

export function hiHaFiltres(f: FiltreExamens): boolean {
  return Object.values(f).some(v => v.trim() !== '')
}

/** Data de l'examen a efectes de filtre: la programada, o la de creació si no està programat. */
export function dataExamen(e: Exam): string {
  return dataLocal(new Date(e.scheduledAt ?? e.createdAt))
}

export function filtraExamens(exams: Exam[], f: FiltreExamens, estatVisual: (e: Exam) => string): Exam[] {
  const text = normalitza(f.cerca.trim())
  return exams.filter(e => {
    if (text && !normalitza(e.title).includes(text)) return false
    if (f.estat && estatVisual(e) !== f.estat) return false
    if (f.modul && (f.modul === SENSE_MODUL ? !!e.modulNom : e.modulNom !== f.modul)) return false
    if (f.autor && e.createdByName !== f.autor) return false
    if (f.cicle && e.cicleNom !== f.cicle) return false
    if (f.grup && e.scheduledGrupId !== f.grup) return false
    if (f.dataDes || f.dataFins) {
      const d = dataExamen(e)
      if (f.dataDes && d < f.dataDes) return false
      if (f.dataFins && d > f.dataFins) return false
    }
    return true
  })
}

const ordena = (l: string[]) => [...new Set(l)].sort((a, b) => a.localeCompare(b, 'ca'))

/** Valors que es poden triar a cada desplegable, segons els exàmens que hi ha. */
export function opcionsFiltre(exams: Exam[]) {
  const grups = new Map<string, string>()
  for (const e of exams) if (e.scheduledGrupId) grups.set(e.scheduledGrupId, e.scheduledGrupName ?? e.scheduledGrupId)
  return {
    moduls: ordena(exams.map(e => e.modulNom).filter((x): x is string => !!x)),
    autors: ordena(exams.map(e => e.createdByName).filter(Boolean)),
    cicles: ordena(exams.map(e => e.cicleNom).filter((x): x is string => !!x)),
    grups: [...grups].map(([id, nom]) => ({ id, nom })).sort((a, b) => a.nom.localeCompare(b.nom, 'ca')),
  }
}

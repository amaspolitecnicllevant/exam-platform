import type { Grup, Modul } from '../types'
import { normalitza } from './filtreAlumnes'

/** Filtres per triar un grup en assignar o programar un examen. Un camp buit vol dir «sense filtre». */
export interface FiltreGrupsAssig {
  text: string
  departament: string
  cicleId: string
  modulId: string
}

export const FILTRE_GRUPS_BUIT: FiltreGrupsAssig = { text: '', departament: '', cicleId: '', modulId: '' }

export function hiHaFiltreGrups(f: FiltreGrupsAssig): boolean {
  return Object.values(f).some(v => v !== '')
}

/**
 * Grups que compleixen els filtres. El departament i el cicle es treuen del mòdul del grup: un grup
 * sense mòdul només surt quan no hi ha filtre de departament, cicle ni mòdul.
 */
export function filtraGrupsAssignacio(grups: Grup[], moduls: Modul[], f: FiltreGrupsAssig): Grup[] {
  const modulDe = new Map(moduls.map(m => [m.id, m]))
  const text = normalitza(f.text.trim())
  return grups.filter(g => {
    if (text && !normalitza(g.name).includes(text) && !normalitza(g.modulNom ?? '').includes(text)) return false
    if (f.departament || f.cicleId || f.modulId) {
      const m = g.modulId ? modulDe.get(g.modulId) : undefined
      if (!m) return false
      if (f.departament && m.departamentNom !== f.departament) return false
      if (f.cicleId && m.cicleId !== f.cicleId) return false
      if (f.modulId && m.id !== f.modulId) return false
    }
    return true
  })
}

const ordena = (l: string[]) => [...new Set(l)].sort((a, b) => a.localeCompare(b, 'ca'))

/** Valors dels desplegables: només els dels grups que tenen mòdul, en cascada (departament → cicle → mòdul). */
export function opcionsGrups(grups: Grup[], moduls: Modul[], f: FiltreGrupsAssig) {
  const modulsDelsGrups = moduls.filter(m => grups.some(g => g.modulId === m.id))
  const enDepartament = modulsDelsGrups.filter(m => !f.departament || m.departamentNom === f.departament)
  const enCicle = enDepartament.filter(m => !f.cicleId || m.cicleId === f.cicleId)
  const cicles = new Map<string, string>()
  for (const m of enDepartament) cicles.set(m.cicleId, m.cicleNom)
  return {
    departaments: ordena(modulsDelsGrups.map(m => m.departamentNom).filter(Boolean)),
    cicles: [...cicles].map(([id, nom]) => ({ id, nom })).sort((a, b) => a.nom.localeCompare(b.nom, 'ca')),
    moduls: [...enCicle].sort((a, b) => a.codi.localeCompare(b.codi)),
  }
}

/** Filtre inicial per a un examen: el seu mòdul, si algun grup hi està lligat. Si no, sense filtres. */
export function filtreGrupsInicial(modulId: string | undefined, grups: Grup[], moduls: Modul[]): FiltreGrupsAssig {
  if (!modulId || !grups.some(g => g.modulId === modulId)) return FILTRE_GRUPS_BUIT
  const m = moduls.find(x => x.id === modulId)
  if (!m) return FILTRE_GRUPS_BUIT
  return { text: '', departament: m.departamentNom, cicleId: m.cicleId, modulId: m.id }
}

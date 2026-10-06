import type { Matricula, Modul, User } from '../types'

/** Filtres de la llista d'alumnes en assignar-los a un grup. Un camp buit vol dir «sense filtre». */
export interface FiltreAlumnes {
  text: string
  cicleId: string
  modulId: string
  curs: string
  nomesSeleccionats: boolean
}

export const FILTRE_BUIT: FiltreAlumnes = {
  text: '', cicleId: '', modulId: '', curs: '', nomesSeleccionats: false,
}

/** Minúscules i sense accents, perquè «Pere» trobi «pérez». */
export function normalitza(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
}

/** Hi ha algun filtre de matrícula (cicle, mòdul o curs) actiu? */
export function filtraPerMatricula(f: FiltreAlumnes): boolean {
  return f.cicleId !== '' || f.modulId !== '' || f.curs !== ''
}

/**
 * Alumnes que compleixen els filtres. Els de cicle, mòdul i curs s'apliquen a la MATEIXA matrícula
 * (un alumne matriculat a un mòdul d'ASIX el curs 2025-26 i a un de DAW el 2026-27 no surt si es
 * demana ASIX + 2026-27). Els alumnes sense cap matrícula només surten quan no hi ha filtre de
 * cicle, mòdul ni curs.
 */
export function filtraAlumnes(
  alumnes: User[],
  matricules: Matricula[],
  moduls: Modul[],
  filtre: FiltreAlumnes,
  seleccionats: ReadonlySet<string>,
): User[] {
  const cicleDelModul = new Map(moduls.map(m => [m.id, m.cicleId]))
  const text = normalitza(filtre.text)
  const perMatricula = filtraPerMatricula(filtre)

  const matriculesPerAlumne = new Map<string, Matricula[]>()
  for (const m of matricules) {
    const llista = matriculesPerAlumne.get(m.alumneId)
    if (llista) llista.push(m)
    else matriculesPerAlumne.set(m.alumneId, [m])
  }

  return alumnes.filter(a => {
    if (filtre.nomesSeleccionats && !seleccionats.has(a.id)) return false
    if (text && !normalitza(a.name).includes(text) && !normalitza(a.email).includes(text)) return false
    if (!perMatricula) return true
    return (matriculesPerAlumne.get(a.id) ?? []).some(m =>
      (filtre.modulId === '' || m.modulId === filtre.modulId) &&
      (filtre.curs === '' || m.curs === filtre.curs) &&
      (filtre.cicleId === '' || cicleDelModul.get(m.modulId) === filtre.cicleId))
  })
}

/** Cursos que apareixen a les matrícules, del més recent al més antic. */
export function cursosDisponibles(matricules: Matricula[]): string[] {
  return [...new Set(matricules.map(m => m.curs))].sort().reverse()
}

/** Mòduls amb alguna matrícula visible, opcionalment només els d'un cicle. */
export function modulsAmbMatricula(moduls: Modul[], matricules: Matricula[], cicleId = ''): Modul[] {
  const amb = new Set(matricules.map(m => m.modulId))
  return moduls.filter(m => amb.has(m.id) && (cicleId === '' || m.cicleId === cicleId))
}

/** Cicles dels mòduls que tenen alguna matrícula visible. */
export function ciclesAmbMatricula(moduls: Modul[], matricules: Matricula[]): { id: string; nom: string }[] {
  const mapa = new Map<string, string>()
  for (const m of modulsAmbMatricula(moduls, matricules)) mapa.set(m.cicleId, m.cicleNom)
  return [...mapa].map(([id, nom]) => ({ id, nom })).sort((a, b) => a.nom.localeCompare(b.nom))
}

/**
 * Filtre inicial en obrir els alumnes d'un grup: el mòdul del grup, el seu cicle i el curs més recent
 * en què hi ha matrícules. Si el grup no té mòdul (o ningú hi és matriculat), sense filtres.
 */
export function filtreInicial(modulId: string | undefined, matricules: Matricula[], moduls: Modul[]): FiltreAlumnes {
  if (!modulId) return FILTRE_BUIT
  const delModul = matricules.filter(m => m.modulId === modulId)
  if (delModul.length === 0) return FILTRE_BUIT
  const cicleId = moduls.find(m => m.id === modulId)?.cicleId ?? ''
  const curs = cursosDisponibles(delModul)[0] ?? ''
  return { ...FILTRE_BUIT, cicleId, modulId, curs }
}

/** Lògica pura de la tria d'alumnes d'un examen (sense React). */

export interface Candidat { id: string; nom: string }

/** Alumnes únics (per id) d'una llista de matrícules del mòdul indicat, ordenats pel nom. */
export function candidatsDelModul(
  matricules: { alumneId: string; alumneNom: string; modulId: string }[], modulId: string | undefined): Candidat[] {
  if (!modulId) return []
  const perId = new Map<string, Candidat>()
  for (const m of matricules) if (m.modulId === modulId) perId.set(m.alumneId, { id: m.alumneId, nom: m.alumneNom })
  return [...perId.values()].sort((a, b) => a.nom.localeCompare(b.nom, 'ca'))
}

/** Candidats que coincideixen amb la cerca (nom, sense distingir majúscules ni accents). */
export function filtraCandidats(candidats: Candidat[], cerca: string): Candidat[] {
  const norm = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
  const q = norm(cerca.trim())
  return q ? candidats.filter(c => norm(c.nom).includes(q)) : candidats
}

/** Ids dels membres d'un grup que són candidats (i no estan ja triats o exclosos). */
export function idsDelGrup(grupAlumneIds: string[], candidats: Candidat[], excloure: Set<string> = new Set()): string[] {
  const valids = new Set(candidats.map(c => c.id))
  return grupAlumneIds.filter(id => valids.has(id) && !excloure.has(id))
}

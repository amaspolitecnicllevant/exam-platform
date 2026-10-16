/** Tipus d'exportació d'un examen i la ruta de l'API que el genera. */
export type TipusExportacio =
  | 'notes' | 'notes-competencies' | 'excel' | 'informe' | 'dificultat' | 'respostes' | 'clau' | 'fitxers' | 'csv-detallat'

export interface OpcionsExportacio {
  /** Només per a `respostes`: amb noms i correus (per defecte, anònim) */
  ambNoms?: boolean
  /** Només per a `respostes`: inclou la resposta model i els criteris (per defecte, sí) */
  ambModel?: boolean
}

const RUTES: Record<TipusExportacio, string> = {
  'notes': 'notes.csv',
  'notes-competencies': 'notes-competencies.csv',
  'excel': 'examen.xlsx',
  'informe': 'informe.md',
  'dificultat': 'dificultat.md',
  'respostes': 'respostes.md',
  'clau': 'clau-alumnes.csv',
  'fitxers': 'fitxers.zip',
  'csv-detallat': 'csv',
}

export function rutaExportacio(examId: string, tipus: TipusExportacio, opcions: OpcionsExportacio = {}): string {
  const base = tipus === 'csv-detallat' ? `/export/exam/${examId}/csv` : `/export/exam/${examId}/${RUTES[tipus]}`
  if (tipus !== 'respostes') return base
  const params = new URLSearchParams()
  if (opcions.ambNoms) params.set('anonim', 'false')
  if (opcions.ambModel === false) params.set('model', 'false')
  const q = params.toString()
  return q ? `${base}?${q}` : base
}

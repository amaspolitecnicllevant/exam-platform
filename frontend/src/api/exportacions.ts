import { descarrega } from './download'
import { rutaExportacio, type OpcionsExportacio, type TipusExportacio } from '../utils/rutesExportacio'

export type { OpcionsExportacio, TipusExportacio } from '../utils/rutesExportacio'

export const descarregaExportacio = (examId: string, tipus: TipusExportacio, nom: string, opcions?: OpcionsExportacio) =>
  descarrega(rutaExportacio(examId, tipus, opcions), nom)

/** Missatge d'error d'una descàrrega: l'API respon JSON, però en baixar blobs arriba com a Blob. */
export async function missatgeErrorExportacio(err: any): Promise<string> {
  const dades = err?.response?.data
  try {
    if (dades instanceof Blob) {
      const json = JSON.parse(await dades.text())
      if (json?.error) return json.error
    } else if (dades?.error) {
      return dades.error
    }
  } catch { /* no era JSON */ }
  if (err?.response?.status === 403) return 'No tens permís per exportar aquest examen.'
  return 'No s\'ha pogut generar l\'exportació. Torna-ho a provar.'
}

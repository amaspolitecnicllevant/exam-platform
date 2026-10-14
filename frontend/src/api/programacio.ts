import client from './client'
import type { Exam, Programacio } from '../types'

export interface ProgramacioPeticio {
  competencies: { codi: string; descripcio: string }[]
  unitats: { codi: string; nom: string; competencies: string[] }[]
}

export const getProgramacio = (modulId: string): Promise<Programacio> =>
  client.get(`/moduls/${modulId}/programacio`).then(r => r.data)

export const desaProgramacio = (modulId: string, data: ProgramacioPeticio): Promise<Programacio> =>
  client.put(`/moduls/${modulId}/programacio`, data).then(r => r.data)

/** CSV: una fila per parella (unitat;competència;descripció;nom de la unitat). Substitueix tota la programació. */
export const importaProgramacioCsv = (modulId: string, fitxer: File): Promise<Programacio> => {
  const form = new FormData()
  form.append('file', fitxer)
  return client.post(`/moduls/${modulId}/programacio/csv`, form).then(r => r.data)
}

export const assignaUnitatsExamen = (examId: string, unitatIds: string[]): Promise<Exam> =>
  client.put(`/exams/${examId}/unitats`, { unitatIds }).then(r => r.data)

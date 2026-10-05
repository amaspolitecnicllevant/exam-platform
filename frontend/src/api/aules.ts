import client from './client'
import type { Aula, Exam } from '../types'

export const getAules = (): Promise<Aula[]> =>
  client.get<Aula[]>('/aules').then(r => r.data)

export const createAula = (nom: string, xarxaCidr: string): Promise<Aula> =>
  client.post<Aula>('/aules', { nom, xarxaCidr }).then(r => r.data)

export const updateAula = (id: string, nom: string, xarxaCidr: string): Promise<Aula> =>
  client.put<Aula>(`/aules/${id}`, { nom, xarxaCidr }).then(r => r.data)

export const deleteAula = (id: string): Promise<void> =>
  client.delete(`/aules/${id}`).then(() => {})

export const assignAulaExamen = (examId: string, aulaId: string): Promise<Exam> =>
  client.patch<Exam>(`/exams/${examId}/aula/${aulaId}`).then(r => r.data)

export const removeAulaExamen = (examId: string): Promise<Exam> =>
  client.delete<Exam>(`/exams/${examId}/aula`).then(r => r.data)

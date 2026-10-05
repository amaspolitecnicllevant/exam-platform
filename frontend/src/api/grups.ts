import client from './client'
import type { Grup } from '../types'

export const getGrups = (): Promise<Grup[]> =>
  client.get('/grups').then(r => r.data)

export const createGrup = (name: string): Promise<Grup> =>
  client.post('/grups', { name }).then(r => r.data)

export const setGrupStudents = (grupId: string, studentIds: string[]): Promise<Grup> =>
  client.put(`/grups/${grupId}/students`, { studentIds }).then(r => r.data)

export const assignExamToGrup = (grupId: string, examId: string): Promise<{ sessionsCreades: number; missatge: string }> =>
  client.post(`/grups/${grupId}/assignar-examen/${examId}`).then(r => r.data)

export const deleteGrup = (grupId: string): Promise<void> =>
  client.delete(`/grups/${grupId}`).then(() => undefined)

export const assignGrupModul = (grupId: string, modulId: string): Promise<import('../types').Grup> =>
  client.patch(`/grups/${grupId}/modul/${modulId}`).then(r => r.data)

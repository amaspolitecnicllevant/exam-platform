import client from './client'
import type { Modul, Imparticio } from '../types'

export const getModuls = (params?: { cicleId?: string; departamentId?: string }): Promise<Modul[]> =>
  client.get('/moduls', { params }).then(r => r.data)

export const createModul = (codi: string, nom: string, cicleId: string): Promise<Modul> =>
  client.post('/moduls', { codi, nom, cicleId }).then(r => r.data)

export const updateModul = (id: string, codi: string, nom: string, cicleId: string): Promise<Modul> =>
  client.put(`/moduls/${id}`, { codi, nom, cicleId }).then(r => r.data)

export const deleteModul = (id: string): Promise<void> =>
  client.delete(`/moduls/${id}`).then(() => undefined)

export const getImparticions = (modulId: string): Promise<Imparticio[]> =>
  client.get(`/moduls/${modulId}/imparticions`).then(r => r.data)

export const addImparticio = (modulId: string, professorId: string, curs: string): Promise<Imparticio> =>
  client.post(`/moduls/${modulId}/imparticions`, { professorId, curs }).then(r => r.data)

export const removeImparticio = (modulId: string, imparticioId: string): Promise<void> =>
  client.delete(`/moduls/${modulId}/imparticions/${imparticioId}`).then(() => undefined)

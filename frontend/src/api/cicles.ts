import client from './client'
import type { Cicle } from '../types'

export const getCicles = (departamentId?: string): Promise<Cicle[]> =>
  client.get('/cicles', { params: departamentId ? { departamentId } : {} }).then(r => r.data)

export const createCicle = (codi: string, nom: string, departamentId: string): Promise<Cicle> =>
  client.post('/cicles', { codi, nom, departamentId }).then(r => r.data)

export const updateCicle = (id: string, codi: string, nom: string, departamentId: string): Promise<Cicle> =>
  client.put(`/cicles/${id}`, { codi, nom, departamentId }).then(r => r.data)

export const deleteCicle = (id: string): Promise<void> =>
  client.delete(`/cicles/${id}`).then(() => undefined)

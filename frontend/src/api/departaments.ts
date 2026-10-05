import client from './client'
import type { Departament, ProfessorDepartament } from '../types'

export const getDepartaments = (): Promise<Departament[]> =>
  client.get('/departaments').then(r => r.data)

export const createDepartament = (nom: string): Promise<Departament> =>
  client.post('/departaments', { nom }).then(r => r.data)

export const deleteDepartament = (id: string): Promise<void> =>
  client.delete(`/departaments/${id}`).then(() => undefined)

export const getProfessorsDepartament = (departamentId: string): Promise<ProfessorDepartament[]> =>
  client.get(`/departaments/${departamentId}/professors`).then(r => r.data)

export const addProfessorDepartament = (departamentId: string, professorId: string, esCap: boolean): Promise<ProfessorDepartament> =>
  client.post(`/departaments/${departamentId}/professors`, { professorId, esCap }).then(r => r.data)

export const removeProfessorDepartament = (departamentId: string, professorId: string): Promise<void> =>
  client.delete(`/departaments/${departamentId}/professors/${professorId}`).then(() => undefined)

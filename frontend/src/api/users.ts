import client from './client'
import type { User, Role } from '../types'

export const getUsers    = () => client.get<User[]>('/users').then(r => r.data)
export const createUser  = (data: { name: string; email: string; password: string; role: Role }) =>
  client.post<User>('/users', data).then(r => r.data)
export const deleteUser  = (id: string) => client.delete(`/users/${id}`)

export const resetPassword = (id: string, password: string) =>
  client.patch(`/users/${id}/password`, { password })

export const importCsv = (file: File, role?: Role) => {
  const form = new FormData()
  form.append('file', file)
  if (role) form.append('role', role)
  return client.post<ImportacioResultat>('/users/import', form).then(r => r.data)
}

export interface ImportacioResultat {
  /** Comptes nous */
  created: number
  /** Correus que ja tenien compte (no es modifiquen) */
  skipped: number
  matriculats: number
  afegitsAGrup: number
  grupsCreats: string[]
  errors: string[]
  /** Contrasenyes generades (files amb la contrasenya en blanc): només es reben ara */
  contrasenyes: { nom: string; email: string; contrasenya: string }[]
}

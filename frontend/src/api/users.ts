import client from './client'
import type { User, Role } from '../types'

export const getUsers    = () => client.get<User[]>('/users').then(r => r.data)
export const createUser  = (data: { name: string; email: string; password: string; role: Role }) =>
  client.post<User>('/users', data).then(r => r.data)
export const deleteUser  = (id: string) => client.delete(`/users/${id}`)

export const resetPassword = (id: string, password: string) =>
  client.patch(`/users/${id}/password`, { password })

/** Canvia la contrasenya pròpia. Les altres sessions es tanquen; la resposta porta un token nou per a aquesta. */
export interface SessioNova { token: string; userId: string; name: string; email: string; role: Role }
export const canviaLaMevaContrasenya = (actual: string, nova: string) =>
  client.post<SessioNova>('/users/me/password', { actual, nova }).then(r => r.data)

/** Tanca totes les sessions obertes d'un usuari (només administradors). */
export const revocaSessions = (id: string) => client.post(`/users/${id}/revoca-sessions`)

/** Llargada mínima de la contrasenya: més exigent per al professorat i l'administració. */
export const minimContrasenya = (role: Role | undefined) => (role === 'STUDENT' ? 8 : 12)

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

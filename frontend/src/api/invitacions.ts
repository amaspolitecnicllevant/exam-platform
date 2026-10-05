import client from './client'
import type { Invitacio, InvitacioPublica } from '../types'
import type { LoginResponse } from './auth'

export const createInvitacio = (modulId: string, curs: string, grupId?: string, maxUses?: number): Promise<Invitacio> =>
  client.post('/invitacions', { modulId, curs, grupId: grupId ?? null, maxUses: maxUses ?? null }).then(r => r.data)

export const getInvitacions = (params?: { modulId?: string; curs?: string }): Promise<Invitacio[]> =>
  client.get('/invitacions', { params }).then(r => r.data)

export const deactivateInvitacio = (id: string): Promise<void> =>
  client.delete(`/invitacions/${id}`).then(() => undefined)

// Endpoints públics (sense auth)
export const getInvitacioPublica = (token: string): Promise<InvitacioPublica> =>
  client.get(`/invitacions/publica/${token}`).then(r => r.data)

export const acceptarConvit = (token: string, nom: string, email: string, password: string): Promise<LoginResponse> =>
  client.post(`/invitacions/publica/${token}/acceptar`, { nom, email, password }).then(r => r.data)

// Per a alumne ja autenticat
export const unirSeConvit = (token: string): Promise<InvitacioPublica> =>
  client.post(`/invitacions/publica/${token}/unir-se`).then(r => r.data)

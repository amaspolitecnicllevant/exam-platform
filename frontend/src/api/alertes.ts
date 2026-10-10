import client from './client'

export type GravetatAlerta = 'ALTA' | 'MITJANA' | 'INFO'

export interface Alerta {
  gravetat: GravetatAlerta
  codi: string
  titol: string
  detall: string
  quantitat: number
  ultima: string | null
}

export const getAlertesSeguretat = () => client.get<Alerta[]>('/admin/alertes').then(r => r.data)

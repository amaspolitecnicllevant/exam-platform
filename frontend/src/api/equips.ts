import client from './client'

export type EstatEquip = 'ALTERAT' | 'SENSE_PLATAFORMA' | 'SENSE_NOTICIES' | 'SENSE_REFERENCIA' | 'PREPARAT'

export interface Equip {
  id: string
  nom: string
  ip: string
  darrerInforme: string
  estat: EstatEquip
  /** Fa molts dies que no informa: el més probable és que no s'hagi encès */
  faTemps: boolean
  diesSenseInformar: number
  arribaPlataforma?: boolean | null
  arribaIsard?: boolean | null
  navegador?: string | null
  discLliureMb?: number | null
  arrencada?: string | null
  usuarisDins?: number | null
  avisos: string[]
  /** Si és ALTERAT: «+» és d'aquest ordinador, «−» és de la referència */
  diferencies: string[]
}

export interface EquipsAula {
  referencia: { fixadaEl: string; origenNom?: string | null } | null
  preparats: number
  alterats: number
  senseNoticies: number
  altres: number
  faTemps: number
  equips: Equip[]
}

export const getEquipsAula = (aulaId: string) =>
  client.get<EquipsAula>(`/aules/${aulaId}/equips`).then(r => r.data)

export const fixaReferenciaEquip = (equipId: string) =>
  client.post(`/equips/${equipId}/referencia`).then(() => {})

export const esborraEquip = (equipId: string) =>
  client.delete(`/equips/${equipId}`).then(() => {})

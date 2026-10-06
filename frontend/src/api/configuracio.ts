import client from './client'

export interface ConfiguracioDto {
  nomCentre: string
  hasLogo: boolean
  colorMarca: string
  cursActiu: string
  duradaDefecte: number
  penalitzacioDefecte: number
  focusLossThreshold: number
  gracePeriodSeconds: number
  dominisOauth: string
  /** Semblança (%) a partir de la qual l'informe de còpies marca una pregunta */
  copiesLlindar: number
  /** El mateix per a les preguntes amb apunts */
  copiesLlindarApunts: number
  /** Hi ha login amb Google configurat al servidor */
  googleActiu: boolean
  /** Els alumnes poden pujar fitxers a les preguntes de lliurament */
  pujadaFitxersActiva: boolean
}

export interface ConfiguracioUpdateRequest {
  nomCentre?: string
  colorMarca?: string
  cursActiu?: string
  duradaDefecte?: number
  penalitzacioDefecte?: number
  focusLossThreshold?: number
  gracePeriodSeconds?: number
  dominisOauth?: string
  copiesLlindar?: number
  copiesLlindarApunts?: number
  pujadaFitxersActiva?: boolean
}

export const getConfiguracio = () =>
  fetch('/api/configuracio').then(r => r.json() as Promise<ConfiguracioDto>)

export const updateConfiguracio = (req: ConfiguracioUpdateRequest) =>
  client.put<ConfiguracioDto>('/configuracio', req).then(r => r.data)

export const uploadLogo = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return client.post('/configuracio/logo', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const deleteLogo = () =>
  client.delete('/configuracio/logo')

export const getLogoUrl = () => '/api/configuracio/logo'

export interface CopiesSeguretat {
  disponible: boolean
  data?: string
  resultat?: 'ok' | 'error'
  missatge?: string
  bytes: number
  retencioDies: number
  antiguitatHores?: number
  remotConfigurat: boolean
  remotResultat?: 'ok' | 'error' | null
  remotMissatge?: string
  alerta: boolean
  motiuAlerta?: string
}

/** Estat de l'última còpia de seguretat (només administradors). */
export const getCopiesSeguretat = () =>
  client.get<CopiesSeguretat>('/configuracio/copies-seguretat').then(r => r.data)

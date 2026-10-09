import client from './client'

export type EstatFila = 'CANVI' | 'IGUAL' | 'IGNORADA' | 'ERROR'

export interface FilaRevisio {
  linia: number
  answerId: string | null
  alumne: string
  codi: string | null
  pregunta: number | null
  enunciat: string | null
  puntsMax: number | null
  notaActual: number | null
  origenActual: 'REVISADA' | 'PROPOSTA' | 'CAP' | null
  notaNova: number | null
  justificacio: string | null
  estat: EstatFila
  motiu: string | null
  /** Substitueix una nota que el professor ja havia revisat */
  sobreescriuRevisada: boolean
}

export interface AlumneRevisio {
  nom: string
  codi: string
  notaAbans: number | null
  notaDespres: number | null
}

export interface Previsualitzacio {
  notesPublicades: boolean
  canvis: number
  iguals: number
  ignorades: number
  errors: number
  files: FilaRevisio[]
  alumnes: AlumneRevisio[]
  avisos: string[]
}

export interface Aplicacio {
  aplicades: number
  saltades: number
  motius: string[]
}

export const previsualitzaRevisioIa = (examId: string, text: string) =>
  client.post<Previsualitzacio>(`/exams/${examId}/revisio-ia/previsualitza`, { text }).then(r => r.data)

export const aplicaRevisioIa = (examId: string, text: string, acceptades: { answerId: string; notaActual: number | null }[]) =>
  client.post<Aplicacio>(`/exams/${examId}/revisio-ia/aplica`, { text, acceptades }).then(r => r.data)

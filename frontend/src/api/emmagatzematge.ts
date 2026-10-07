import client from './client'
import type { Disc } from '../utils/emmagatzematge'

export type Agrupacio = 'professor' | 'departament' | 'cicle' | 'modul' | 'examen'

export interface FilaEspai {
  clau: string
  nom: string
  detall: string | null
  examens: number
  fitxersPregunta: number
  midaPregunta: number
  lliuraments: number
  midaLliuraments: number
  total: number
}

export interface Emmagatzematge {
  taula: { agrupa: Agrupacio; files: FilaEspai[]; total: FilaEspai }
  disc: Disc
}

export const getEmmagatzematge = (agrupa: Agrupacio) =>
  client.get<Emmagatzematge>('/admin/emmagatzematge', { params: { agrupa } }).then(r => r.data)

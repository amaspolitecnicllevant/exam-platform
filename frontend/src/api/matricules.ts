import client from './client'
import type { Matricula } from '../types'

export const getMatriculesByAlumne = (alumneId: string): Promise<Matricula[]> =>
  client.get('/matricules', { params: { alumneId } }).then(r => r.data)

export const getMatriculesByModul = (modulId: string, curs: string): Promise<Matricula[]> =>
  client.get('/matricules', { params: { modulId, curs } }).then(r => r.data)

export const enroll = (alumneId: string, modulId: string, curs: string): Promise<Matricula> =>
  client.post('/matricules', { alumneId, modulId, curs }).then(r => r.data)

/** Matricula molts alumnes al mateix mòdul i curs (els que ja hi són es compten però no fallen). */
export const enrollLot = (alumneIds: string[], modulId: string, curs: string):
  Promise<{ matriculades: Matricula[]; jaMatriculats: number }> =>
  client.post('/matricules/lot', { alumneIds, modulId, curs }).then(r => r.data)

export const unenroll = (matriculaId: string): Promise<void> =>
  client.delete(`/matricules/${matriculaId}`).then(() => undefined)

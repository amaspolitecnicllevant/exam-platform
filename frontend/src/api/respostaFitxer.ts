import client from './client'
import { descarrega } from './download'
import type { Answer } from '../types'

/** L'alumne puja (o substitueix) el fitxer d'una pregunta de lliurament. */
export const pujaFitxerResposta = (sessionId: string, questionId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return client.post<Answer>(`/sessions/${sessionId}/questions/${questionId}/file`, form).then(r => r.data)
}

export const esborraFitxerResposta = (sessionId: string, questionId: string) =>
  client.delete<Answer>(`/sessions/${sessionId}/questions/${questionId}/file`).then(r => r.data)

/** Descàrrega: l'alumne propietari o el professor que gestiona l'examen. */
export const descarregaFitxerResposta = (sessionId: string, questionId: string, nom: string) =>
  descarrega(`/sessions/${sessionId}/questions/${questionId}/file`, nom)

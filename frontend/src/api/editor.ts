import client from './client'
import type { Question, QuestionFile, QuestionType } from '../types'

/** Contingut d'una pregunta per crear-la o reescriure-la (el backend aplica les regles d'importació). */
export interface PreguntaEdicio {
  tipus: QuestionType
  enunciat: string
  punts?: number
  opcions?: string[]
  correctChoice?: string
  modelResposta?: string
  outputContains?: string
  outputExact?: string
  outputRegex?: string
  testScript?: string
  claus?: string
  ra?: string
  dificultat?: string
  barrejarOpcions?: boolean
  ambApunts?: boolean
  formatsPermesos?: string[]
  /** Només en crear: lloc (1 = primera); sense valor, al final. */
  posicio?: number
}

export interface EstatEdicio { editable: boolean; motiu: string | null }

export const getEstatEdicio = (examId: string) =>
  client.get<EstatEdicio>(`/exams/${examId}/editable`).then(r => r.data)

export const afegeixPregunta = (examId: string, p: PreguntaEdicio) =>
  client.post<Question>(`/exams/${examId}/questions`, p).then(r => r.data)

export const actualitzaPregunta = (examId: string, questionId: string, p: PreguntaEdicio) =>
  client.put<Question>(`/exams/${examId}/questions/${questionId}`, p).then(r => r.data)

export const eliminaPregunta = (examId: string, questionId: string) =>
  client.delete(`/exams/${examId}/questions/${questionId}`)

export const mouPregunta = (examId: string, questionId: string, posicio: number) =>
  client.post(`/exams/${examId}/questions/${questionId}/move`, { posicio })

export const pujaImatge = (questionId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return client.post<QuestionFile>(`/questions/${questionId}/images`, form).then(r => r.data)
}

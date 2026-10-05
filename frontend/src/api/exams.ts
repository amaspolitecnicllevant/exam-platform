import client from './client'
import type { Exam, ExecutionResult } from '../types'

export const getMyExams   = () => client.get<Exam[]>('/exams/mine').then(r => r.data)
export const getPublished = () => client.get<Exam[]>('/exams/published').then(r => r.data)
export const getExam      = (id: string) => client.get<Exam>(`/exams/${id}`).then(r => r.data)
export const publishExam   = (id: string) => client.post<Exam>(`/exams/${id}/publish`).then(r => r.data)
export const unpublishExam = (id: string) => client.post<Exam>(`/exams/${id}/unpublish`).then(r => r.data)
export const closeExam     = (id: string) => client.post<Exam>(`/exams/${id}/close`).then(r => r.data)
export const duplicateExam = (id: string) => client.post<Exam>(`/exams/${id}/duplicate`).then(r => r.data)
export const deleteExam    = (id: string) => client.delete(`/exams/${id}`)
export const scheduleExam  = (id: string, scheduledAt: string, grupId: string) =>
  client.post<Exam>(`/exams/${id}/schedule`, { scheduledAt, grupId }).then(r => r.data)
export const unscheduleExam = (id: string) =>
  client.delete<Exam>(`/exams/${id}/schedule`).then(r => r.data)
export const reopenWindow = (id: string) =>
  client.post<Exam>(`/exams/${id}/reopen-window`).then(r => r.data)

export const createFromMd = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return client.post<Exam>('/exams', form).then(r => r.data)
}

export const patchQuestion = (examId: string, questionId: string, data: { correctChoice?: string; anulada?: boolean; ra?: string; dificultat?: string; barrejarOpcions?: boolean; ambApunts?: boolean }) =>
  client.patch<{ id: string }>(`/exams/${examId}/questions/${questionId}`, data).then(r => r.data)

export interface ExamSettings {
  penalitzacioChoice?: number
  unaPreguntaPerPantalla?: boolean
  title?: string
  durada?: number
}

/** Canvis parcials: els camps no indicats no es modifiquen */
export const updateExamSettings = (examId: string, settings: ExamSettings) =>
  client.patch<import('../types').Exam>(`/exams/${examId}/settings`, settings).then(r => r.data)

export const runAnswer = (answerId: string) =>
  client.post<ExecutionResult>(`/executions/${answerId}/run`).then(r => r.data)

export const setManualScore = (answerId: string, score: number) =>
  client.patch(`/executions/${answerId}/score`, { manualScore: score }).then(r => r.data)

export const getCopies = (examId: string) =>
  client.get<import('../types').CopiesInforme>(`/exams/${examId}/copies`).then(r => r.data)

export const getRecuperacio = (examId: string) =>
  client.get<import('../types').Recuperacio>(`/exams/${examId}/recuperacio`).then(r => r.data)
export const crearGrupRecuperacio = (examId: string, nom: string, alumneIds: string[]) =>
  client.post<import('../types').Grup>(`/exams/${examId}/recuperacio/grup`, { nom, alumneIds }).then(r => r.data)
export const getExamStats = (examId: string) =>
  client.get<import('../types').ExamStats>(`/exams/${examId}/stats`).then(r => r.data)

export const setComment = (answerId: string, comentari: string) =>
  client.patch<import('../types').Answer>(`/executions/${answerId}/comment`, { comentari }).then(r => r.data)

export const acceptProposal = (answerId: string) =>
  client.post<import('../types').Answer>(`/executions/${answerId}/accept`).then(r => r.data)

export const acceptAllProposals = (examId: string, sessionId?: string) =>
  client.post<{ acceptades: number }>(`/executions/exam/${examId}/accept-all`, null,
    { params: sessionId ? { sessionId } : undefined }).then(r => r.data)

export const exportCsv = (examId: string) =>
  client.get(`/export/exam/${examId}/csv`, { responseType: 'blob' })

export const assignModul = (examId: string, modulId: string): Promise<Exam> =>
  client.patch<Exam>(`/exams/${examId}/modul/${modulId}`).then(r => r.data)

export const publicarNotes = (examId: string): Promise<Exam> =>
  client.post<Exam>(`/exams/${examId}/publicar-notes`).then(r => r.data)

export const ocultarNotes = (examId: string): Promise<Exam> =>
  client.post<Exam>(`/exams/${examId}/ocultar-notes`).then(r => r.data)

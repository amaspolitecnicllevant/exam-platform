import client from './client'
import type { Session, Answer } from '../types'

export const startSession  = (examId: string) =>
  client.post<Session>(`/sessions/start/${examId}`).then(r => r.data)

export const saveAnswer    = (sessionId: string, questionId: string, contingut: string) =>
  client.put<Answer>(`/sessions/${sessionId}/answers`, { questionId, contingut }).then(r => r.data)

export const reportFocusLoss = (sessionId: string) =>
  client.post(`/sessions/${sessionId}/focus-loss`).catch(() => {})

export const submitSession = (sessionId: string) =>
  client.post<Session>(`/sessions/${sessionId}/submit`).then(r => r.data)

export const getSession    = (sessionId: string) =>
  client.get<Session>(`/sessions/${sessionId}`).then(r => r.data)

export const getMySubmitted = () =>
  client.get<Session[]>('/sessions/my').then(r => r.data)

export const resetSession = (sessionId: string) =>
  client.post<Session>(`/sessions/${sessionId}/reset`).then(r => r.data)

export const restartSession = (examId: string) =>
  client.post<Session>(`/sessions/restart/${examId}`).then(r => r.data)

export const getSessionsByExam = (examId: string) =>
  client.get<Session[]>(`/sessions/exam/${examId}`).then(r => r.data)

export const getMonitor = (examId: string) =>
  client.get<MonitorEntry[]>(`/sessions/exam/${examId}/monitor`).then(r => r.data)

export interface MonitorEntry {
  sessionId: string
  studentId: string
  studentName: string
  studentEmail: string
  clientIp: string | null
  startedAt?: string
  submittedAt: string | null
  status: 'IN_PROGRESS' | 'SUBMITTED'
  focusLossCount: number
  answersCount: number
}

export const getHistorial = () =>
  client.get<import('../types').HistorialItem[]>('/sessions/my/historial').then(r => r.data)

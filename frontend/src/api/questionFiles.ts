import client from './client'
import { descarrega } from './download'
import type { QuestionFile } from '../types'

export const listQuestionFiles = (questionId: string) =>
  client.get<QuestionFile[]>(`/questions/${questionId}/files`).then(r => r.data)

export const uploadQuestionFile = (questionId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return client.post<QuestionFile>(`/questions/${questionId}/files`, form).then(r => r.data)
}

export const deleteQuestionFile = (questionId: string, fileId: string) =>
  client.delete(`/questions/${questionId}/files/${fileId}`)

export const downloadQuestionFile = (fileId: string, filename: string) =>
  descarrega(`/files/${fileId}/download`, filename)

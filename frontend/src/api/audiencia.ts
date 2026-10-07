import client from './client'

export interface AlumneAcces { id: string; nom: string; email: string; estat: 'PENDENT' | 'EN_CURS' | 'ENTREGAT' }
export interface LlistaAlumnes { restringit: boolean; alumnes: AlumneAcces[] }

export const getAlumnesExamen = (examId: string) =>
  client.get<LlistaAlumnes>(`/exams/${examId}/alumnes`).then(r => r.data)

export const afegeixAlumnesExamen = (examId: string, alumneIds: string[]) =>
  client.post<LlistaAlumnes>(`/exams/${examId}/alumnes`, { alumneIds }).then(r => r.data)

export const treuAlumneExamen = (examId: string, alumneId: string) =>
  client.delete(`/exams/${examId}/alumnes/${alumneId}`)

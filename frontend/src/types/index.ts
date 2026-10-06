export type Role = 'ADMIN' | 'PROFESSOR' | 'STUDENT'
export type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'
export type SessionStatus = 'IN_PROGRESS' | 'SUBMITTED'
export type QuestionType = 'TEXT' | 'SHORT' | 'LONG' | 'CHOICE' | 'BASH_CMD' | 'PS_CMD' | 'BASH_SCRIPT' | 'PS_SCRIPT' | 'JAVA_PROG' | 'HTML_CSS' | 'FILE_UPLOAD' | 'SECTION'

export interface QuestionFile {
  id: string
  questionId: string
  filename: string
  contentType: string | null
  fileSize: number
}

export interface User {
  id: string
  name: string
  email: string
  role: Role
}

export interface Question {
  id: string
  ordre: number
  tipus: QuestionType
  enunciat: string
  punts: number
  modelResposta?: string
  outputContains?: string
  outputExact?: string
  outputRegex?: string
  testScript?: string
  claus?: string
  choices?: string[]
  correctChoice?: string
  /** CHOICE: cada alumne veu les opcions en un ordre diferent */
  barrejarOpcions: boolean
  anulada: boolean
  /** Es poden fer servir apunts (en paper) en aquesta pregunta */
  ambApunts: boolean
  ra?: string
  dificultat?: 'baixa' | 'mitjana' | 'alta'
  files?: QuestionFile[]
  /** Només FILE_UPLOAD: extensions (sense punt) que l'alumne pot pujar */
  formatsPermesos?: string[]
}

export interface Departament {
  id: string
  nom: string
  createdAt: string
}

export interface Cicle {
  id: string
  codi: string
  nom: string
  departamentId: string
  departamentNom: string
}

export interface Modul {
  id: string
  codi: string
  nom: string
  cicleId: string
  cicleNom: string
  departamentNom: string
}

export interface Imparticio {
  id: string
  professorId: string
  professorNom: string
  modulId: string
  modulCodi: string
  curs: string
}

export interface Matricula {
  id: string
  alumneId: string
  alumneNom: string
  modulId: string
  modulNom: string
  modulCodi: string
  curs: string
}

export interface Invitacio {
  id: string
  token: string
  modulId: string
  modulCodi: string
  modulNom: string
  cicleNom: string
  curs: string
  createdByNom: string
  expiresAt: string
  usesCount: number
  maxUses: number | null
  active: boolean
  grupId?: string
  grupNom?: string
}

export interface InvitacioPublica {
  modulCodi: string
  modulNom: string
  cicleNom: string
  departamentNom: string
  curs: string
  professorNom: string
}

export interface ProfessorDepartament {
  professorId: string
  professorNom: string
  professorEmail: string
  departamentId: string
  departamentNom: string
  esCap: boolean
}

export interface Aula {
  id: string
  nom: string
  xarxaCidr: string
}

export interface Exam {
  id: string
  title: string
  durada: number
  instruccions?: string
  status: ExamStatus
  createdByName: string
  createdAt: string
  scheduledAt?: string
  scheduledGrupId?: string
  scheduledGrupName?: string
  penalitzacioChoice: number
  modulId?: string
  modulNom?: string
  cicleNom?: string
  aulaId?: string
  aulaNom?: string
  aulaCidr?: string
  notesVisibles: boolean
  /** L'alumne veu una sola pregunta per pantalla */
  unaPreguntaPerPantalla: boolean
  questions: Question[]
}

export interface Answer {
  id: string
  questionId: string
  contingut?: string
  executionOutput?: string
  autoScore?: number
  manualScore?: number
  /** Motius de la nota proposada, un per línia */
  autoFeedback?: string
  /** Comentari del professor (l'alumne el veu quan es publiquen les notes) */
  comentari?: string
  /** Fitxer pujat (preguntes de lliurament): nom original i mida en bytes */
  fitxerNom?: string
  fitxerMida?: number
}

export interface PreguntaStats {
  id: string
  ordre: number
  tipus: QuestionType
  enunciat: string
  punts: number
  bonus: boolean
  ra?: string
  respostes: number
  senseResposta: number
  mitjanaPunts: number | null
  percentRendiment: number | null
  percentCorrectes: number | null
  correcta: string | null
  opcions: Record<string, number> | null
}

export interface ExamStats {
  entregats: number
  sessions: number
  respostesPendents: number
  mitjana: number | null
  mediana: number | null
  minima: number | null
  maxima: number | null
  percentAprovats: number | null
  /** Alumnes per franja: [0,1), [1,2), … , [9,10] */
  histograma: number[]
  preguntes: PreguntaStats[]
}

export interface HistorialItem {
  sessionId: string
  examId: string
  examTitle: string
  modulId?: string
  modulNom?: string
  submittedAt?: string
  notesVisibles: boolean
  /** Només si el professor ha publicat les notes */
  nota: number | null
}

export interface CopiesCoincidencia {
  preguntaId: string
  ordre: number
  tipus: QuestionType
  ambApunts: boolean
  enunciat: string
  semblanca: number
  respostaA: string
  respostaB: string
  /** Intervals [inici, fi) coincidents a cada resposta */
  marquesA: [number, number][]
  marquesB: [number, number][]
}

export interface CopiesParella {
  sessioA: string
  alumneA: string
  sessioB: string
  alumneB: string
  maxSemblanca: number
  coincidencies: CopiesCoincidencia[]
  erradesTestComunes: number[]
}

export interface CopiesInforme {
  llindarPercent: number
  llindarApuntsPercent: number
  minErradesTest: number
  entregats: number
  parelles: CopiesParella[]
}

export interface Session {
  id: string
  examId: string
  examTitle: string
  studentId: string
  studentName: string
  status: SessionStatus
  /** Absent si la sessió s'ha creat per avançat i l'alumne encara no ha obert l'examen */
  startedAt?: string
  submittedAt?: string
  focusLossCount: number
  notesVisibles: boolean
  modulNom?: string
  answers: Answer[]
}

export interface Grup {
  id: string
  name: string
  modulId?: string
  modulNom?: string
  students: User[]
  createdAt: string
}

export interface ExecutionResult {
  output: string
  exitCode: number
  durationMs: number
  succeeded: boolean
}

export interface RecuperacioCandidat {
  alumneId: string
  nom: string
  email: string
  motiu: 'SUSPES' | 'NO_PRESENTAT'
  /** Nota sobre 10 (null si no ha entregat). */
  nota: number | null
  notaProvisional: boolean
}

export interface Recuperacio {
  respostesPendents: number
  candidats: RecuperacioCandidat[]
}

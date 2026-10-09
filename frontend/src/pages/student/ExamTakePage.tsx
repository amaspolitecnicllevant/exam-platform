import { idsReferenciats } from '../../utils/imatges'
import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import Editor from '@monaco-editor/react'
import '../../monaco'
import Layout from '../../components/Layout'
import Md from '../../components/Md'
import { getExam, runAnswer } from '../../api/exams'
import { startSession, saveAnswer, submitSession, reportFocusLoss, restartSession } from '../../api/sessions'
import { downloadQuestionFile } from '../../api/questionFiles'
import { useAuth } from '../../context/AuthContext'
import { useConfiguracio } from '../../context/ConfiguracioContext'
import { agafaPestanyaExamen, avisaIntrusa, escoltaIntrusos, type EstatPestanya } from '../../utils/unaPestanya'
import { pujaFitxerResposta, esborraFitxerResposta, descarregaFitxerResposta } from '../../api/respostaFitxer'
import { errorFitxer, formatMida, llistaFormats, MIDA_MAXIMA_FITXER } from '../../utils/fitxers'
import type { Exam, Session, QuestionType, ExecutionResult } from '../../types'

const SCRIPT_TYPES: QuestionType[] = ['BASH_CMD', 'PS_CMD', 'BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
const MULTILINE_TYPES: QuestionType[] = ['BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
const LANG_MAP: Partial<Record<QuestionType, string>> = {
  BASH_CMD: 'shell', PS_CMD: 'powershell',
  BASH_SCRIPT: 'shell', PS_SCRIPT: 'powershell',
  JAVA_PROG: 'java',
  HTML_CSS: 'html',
}

const JAVA_STUB = `public class Main {
    public static void main(String[] args) {
        // Escriu el teu codi aquí
    }
}`

function hashStr(s: string): number {
  let h = 0
  for (const c of s) h = (Math.imul(31, h) + c.charCodeAt(0)) | 0
  return h
}

function seededShuffle<T>(arr: T[], seed: number): T[] {
  let h = seed
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    h = (Math.imul(h, 1664525) + 1013904223) | 0
    const j = Math.abs(h) % (i + 1);
    [a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * Opcions de test en ordre propi de cada alumne (seed null = ordre original, p. ex. "Totes les anteriors").
 * El valor desat continua sent la lletra original.
 */
function shuffledChoices(choices: string[], seed: number | null): { letter: string; text: string }[] {
  const opcions = choices.map(c => ({ letter: c.charAt(0), text: c.replace(/^[a-zA-Z]\)\s*/, '') }))
  return seed === null ? opcions : seededShuffle(opcions, seed)
}

function shuffleWithinSections<T extends { tipus: QuestionType }>(questions: T[], seed: number): T[] {
  const sections: { header: T | null; qs: T[] }[] = []
  let cur: { header: T | null; qs: T[] } = { header: null, qs: [] }
  for (const q of questions) {
    if (q.tipus === 'SECTION') {
      sections.push(cur)
      cur = { header: q, qs: [] }
    } else {
      cur.qs.push(q)
    }
  }
  sections.push(cur)

  const result: T[] = []
  let s = seed
  for (const sec of sections) {
    if (sec.header) result.push(sec.header)
    s = (Math.imul(s, 1664525) + 1013904223) | 0
    result.push(...seededShuffle(sec.qs, s))
  }
  return result
}

/** Només una pestanya pot fer l'examen: una segona còpia queda bloquejada i es registra. */
export default function ExamTakePage() {
  const [pestanya, setPestanya] = useState<EstatPestanya | null>(null)
  useEffect(() => {
    let viu = true
    let allibera = () => {}
    agafaPestanyaExamen().then(r => {
      allibera = r.allibera
      if (viu) setPestanya(r.estat); else r.allibera()
    })
    return () => { viu = false; allibera() }
  }, [])

  useEffect(() => { if (pestanya === 'duplicada') avisaIntrusa() }, [pestanya])

  if (pestanya === null) return <Layout examMode><p className="text-gray-400 p-8">Carregant…</p></Layout>
  if (pestanya === 'duplicada') {
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-3 text-center px-4">
          <div className="text-5xl">🚫</div>
          <p className="text-xl font-semibold text-gray-800">Aquest examen ja és obert en una altra pestanya</p>
          <p className="text-gray-600 max-w-md">
            Només es pot tenir una pestanya oberta durant l'examen. Tanca aquesta i continua a l'altra.
            Aquest intent ha quedat registrat.
          </p>
        </div>
      </Layout>
    )
  }
  return <ExamTake />
}

function ExamTake() {
  const { examId }              = useParams<{ examId: string }>()
  const navigate                = useNavigate()
  const { user: authUser }      = useAuth()
  const { config }              = useConfiguracio()
  const [exam, setExam]             = useState<Exam | null>(null)
  const [session, setSession]       = useState<Session | null>(null)
  const [orderedQs, setOrderedQs]   = useState<Exam['questions']>([])
  const [answers, setAnswers]       = useState<Record<string, string>>({})
  const [outputs, setOutputs]       = useState<Record<string, ExecutionResult>>({})
  const [runErrors, setRunErrors]   = useState<Record<string, string>>({})
  const [running, setRunning]       = useState<string | null>(null)
  const [submitted, setSubmit]      = useState(false)
  const [focusWarning, setFocusWarning] = useState(false)
  const [secondsLeft, setSecondsLeft]   = useState<number | null>(null)
  const [loadError, setLoadError]       = useState<string | null>(null)
  const [confirmRestart, setConfirmRestart] = useState(false)
  const [restarting, setRestarting]         = useState(false)
  const [htmlPreviews, setHtmlPreviews] = useState<Record<string, boolean>>({})
  const [current, setCurrent] = useState(0)
  const [saveError, setSaveError] = useState('')
  // Preguntes de lliurament de fitxer: fitxer pujat per pregunta, pujada en curs i errors
  const [fitxers, setFitxers] = useState<Record<string, { nom: string; mida: number }>>({})
  const [pujant, setPujant] = useState<string | null>(null)
  const [errorsFitxer, setErrorsFitxer] = useState<Record<string, string>>({})
  // Estat del desament per a l'alumne: 'pendent' (escrivint), 'desant', 'desat', 'error'
  const [estatDesat, setEstatDesat] = useState<'inici' | 'pendent' | 'desant' | 'desat' | 'error'>('inici')
  const desamentsEnCurs = useRef(0)
  const [avisTemps, setAvisTemps] = useState('')
  const saveTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({})
  // Valors escrits que encara no s'han enviat, i desaments enviats que no han acabat:
  // abans d'entregar cal enviar els uns i esperar els altres (si no, els últims canvis es perden)
  const valorsPendents = useRef<Record<string, string>>({})
  const desamentsVius = useRef<Set<Promise<unknown>>>(new Set())
  // Hora de final (ms). El compte enrere es recalcula a partir d'aquí: un comptador que resta 1
  // cada segon s'endarrereix quan la pestanya és en segon pla
  const [deadline, setDeadline] = useState<number | null>(null)
  const avisosMostrats = useRef({ deu: false, un: false })
  // Els diàlegs natius (confirm) fan perdre el focus a la finestra: no és una sortida de l'examen.
  // Fins a aquest instant (ms) no es registren pèrdues de focus.
  const ignoraFocusFins = useRef(0)
  /** confirm() sense que el diàleg compti com a pèrdua de focus (l'esdeveniment arriba quan es tanca). */
  const confirmaSenseFocus = (missatge: string): boolean => {
    const resposta = window.confirm(missatge)
    ignoraFocusFins.current = Date.now() + 1000
    return resposta
  }

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (submitted) return
      e.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [submitted])

  useEffect(() => {
    if (submitted || !session) return
    let lastLoss = 0
    const onLoss = () => {
      const now = Date.now()
      if (now < ignoraFocusFins.current) return
      if (now - lastLoss < 1000) return
      lastLoss = now
      setFocusWarning(true)
      reportFocusLoss(session.id)
    }
    const onVisibility = () => { if (document.hidden) onLoss() }
    const onBlur = () => {
      if (document.hidden) return
      setTimeout(() => {
        if (document.activeElement?.tagName === 'IFRAME') return
        onLoss()
      }, 50)
    }
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
    // Una altra pestanya s'ha intentat obrir durant l'examen: també és una pèrdua de focus
    let ultimaIntrusa = 0
    const tancaCanal = escoltaIntrusos(() => {
      const ara = Date.now()
      if (ara - ultimaIntrusa < 5000) return
      ultimaIntrusa = ara
      setFocusWarning(true)
      reportFocusLoss(session.id)
    })
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('blur', onBlur)
      tancaCanal()
    }
  }, [submitted, session])

  useEffect(() => {
    if (!examId) return
    Promise.all([getExam(examId), startSession(examId)]).then(([e, s]) => {
      setExam(e); setSession(s)
      setOrderedQs(shuffleWithinSections(e.questions, hashStr(s.id)))
      try { setCurrent(Number(sessionStorage.getItem(`exam-pos-${s.id}`)) || 0) } catch { /* sense emmagatzematge */ }
      const init: Record<string, string> = {}
      s.answers.forEach(a => { if (a.contingut) init[a.questionId] = a.contingut })
      const fitxersInicials: Record<string, { nom: string; mida: number }> = {}
      s.answers.forEach(a => { if (a.fitxerNom) fitxersInicials[a.questionId] = { nom: a.fitxerNom, mida: a.fitxerMida ?? 0 } })
      setFitxers(fitxersInicials)
      // Inicialitza stub Java per les preguntes sense resposta
      e.questions.forEach(q => {
        if (q.tipus === 'JAVA_PROG' && !init[q.id]) init[q.id] = JAVA_STUB
      })
      setAnswers(init)
      if (s.status === 'SUBMITTED') {
        setConfirmRestart(true)
        return
      }
      // startOrResume sempre fixa startedAt; si no hi fos, el rellotge comença ara
      setDeadline(new Date(s.startedAt ?? Date.now()).getTime() + e.durada * 60_000)
    }).catch(err => {
      const msg = err?.response?.data?.error || err?.message || 'Error desconegut'
      setLoadError(msg)
    })
  }, [examId])

  useEffect(() => {
    if (deadline === null || submitted) return
    const actualitza = () => setSecondsLeft(Math.max(0, Math.floor((deadline - Date.now()) / 1000)))
    actualitza()
    const interval = setInterval(actualitza, 1000)
    return () => clearInterval(interval)
  }, [deadline, submitted])

  useEffect(() => {
    if (secondsLeft === null || submitted) return
    if (secondsLeft <= 0) { handleAutoSubmit(); return }
    if (secondsLeft <= 60 && !avisosMostrats.current.un) {
      avisosMostrats.current = { deu: true, un: true }
      setAvisTemps('Queda 1 minut. L\'examen s\'entregarà automàticament quan s\'acabi el temps.')
    } else if (secondsLeft <= 600 && !avisosMostrats.current.deu) {
      avisosMostrats.current.deu = true
      setAvisTemps('Queden 10 minuts. Repassa les respostes i recorda entregar l\'examen.')
    }
  }, [secondsLeft, submitted])

  const handleAutoSubmit = async () => {
    if (!session || submitted) return
    setSubmit(true)
    await desaTot()
    try { await submitSession(session.id) } catch { /* el servidor ja l'haurà tancat */ }
    navigate(`/student/sessions/${session.id}/results`, { replace: true })
  }

  /** Envia el valor pendent d'una pregunta (si n'hi ha). */
  const envia = useCallback((questionId: string) => {
    if (!session || !(questionId in valorsPendents.current)) return
    const value = valorsPendents.current[questionId]
    delete valorsPendents.current[questionId]
    delete saveTimers.current[questionId]
    desamentsEnCurs.current++
    setEstatDesat('desant')
    const p: Promise<unknown> = saveAnswer(session.id, questionId, value)
      .then(() => {
        setSaveError('')
        desamentsEnCurs.current--
        if (desamentsEnCurs.current === 0) setEstatDesat('desat')
      })
      .catch((err: any) => {
        desamentsEnCurs.current--
        setEstatDesat('error')
        setSaveError(
          err?.response?.data?.error || err?.response?.data?.message
            || 'Sense connexió amb el servidor: la resposta no s\'ha desat')
      })
      .finally(() => { desamentsVius.current.delete(p) })
    desamentsVius.current.add(p)
  }, [session])

  /** Envia tots els canvis pendents i espera que acabin tots els desaments. */
  const desaTot = async () => {
    for (const questionId of Object.keys(saveTimers.current)) clearTimeout(saveTimers.current[questionId])
    Object.keys(valorsPendents.current).forEach(envia)
    await Promise.allSettled([...desamentsVius.current])
  }

  const handleChange = useCallback((questionId: string, value: string) => {
    setAnswers(prev => ({ ...prev, [questionId]: value }))
    if (!session) return
    clearTimeout(saveTimers.current[questionId])
    valorsPendents.current[questionId] = value
    setEstatDesat('pendent')
    saveTimers.current[questionId] = setTimeout(() => envia(questionId), 500)
  }, [session, envia])

  /** Puja (o substitueix) el fitxer d'una pregunta de lliurament. */
  const handlePuja = async (q: Q, fitxer: File) => {
    if (!session || submitted) return
    const error = errorFitxer(fitxer.name, fitxer.size, q.formatsPermesos ?? [], MIDA_MAXIMA_FITXER)
    if (error) { setErrorsFitxer(prev => ({ ...prev, [q.id]: error })); return }
    setErrorsFitxer(prev => { const n = { ...prev }; delete n[q.id]; return n })
    setPujant(q.id)
    try {
      const resposta = await pujaFitxerResposta(session.id, q.id, fitxer)
      setFitxers(prev => ({ ...prev, [q.id]: { nom: resposta.fitxerNom ?? fitxer.name, mida: resposta.fitxerMida ?? fitxer.size } }))
      setAnswers(prev => ({ ...prev, [q.id]: resposta.fitxerNom ?? fitxer.name }))
    } catch (err: any) {
      const msg = err?.response?.status === 413
        ? `El fitxer supera la mida màxima de ${Math.round(MIDA_MAXIMA_FITXER / (1024 * 1024))} MB.`
        : err?.response?.data?.error || 'No s\'ha pogut pujar el fitxer. Torna-ho a provar.'
      setErrorsFitxer(prev => ({ ...prev, [q.id]: msg }))
    } finally {
      setPujant(null)
    }
  }

  const handleEsborraFitxer = async (q: Q) => {
    if (!session || submitted) return
    if (!confirmaSenseFocus('Vols esborrar el fitxer pujat?')) return
    try {
      await esborraFitxerResposta(session.id, q.id)
      setFitxers(prev => { const n = { ...prev }; delete n[q.id]; return n })
      setAnswers(prev => { const n = { ...prev }; delete n[q.id]; return n })
    } catch (err: any) {
      setErrorsFitxer(prev => ({ ...prev, [q.id]: err?.response?.data?.error || 'No s\'ha pogut esborrar el fitxer.' }))
    }
  }

  const handleRun = async (questionId: string) => {
    if (!session) return
    setRunning(questionId)
    setRunErrors(prev => { const next = { ...prev }; delete next[questionId]; return next })
    try {
      clearTimeout(saveTimers.current[questionId])
      delete saveTimers.current[questionId]
      delete valorsPendents.current[questionId]
      const saved = await saveAnswer(session.id, questionId, answers[questionId] ?? '')
      if (desamentsEnCurs.current === 0) setEstatDesat('desat')
      setSession(prev => {
        if (!prev) return prev
        const existing = prev.answers.find(a => a.questionId === questionId)
        if (existing) return prev
        return { ...prev, answers: [...prev.answers, saved] }
      })
      const result = await runAnswer(saved.id)
      setOutputs(prev => ({ ...prev, [questionId]: result }))
    } catch (err: any) {
      const msg = err?.response?.data?.error || err?.message || 'Error desconegut executant el codi'
      setRunErrors(prev => ({ ...prev, [questionId]: msg }))
    } finally {
      setRunning(null)
    }
  }

  type Q = Exam['questions'][number]

  const renderSection = (q: Q) => (
    <div key={q.id} className="pt-6 pb-2">
      <div className="flex items-center gap-3">
        <div className="flex-1 h-px bg-brand-200" />
        <span className="text-brand-700 font-semibold text-sm px-3 py-1 bg-brand-50 rounded-full border border-brand-200">{q.enunciat}</span>
        <div className="flex-1 h-px bg-brand-200" />
      </div>
    </div>
  )

  const renderQuestion = (q: Q, num: number) => {
    const isExec   = SCRIPT_TYPES.includes(q.tipus)
    const isML     = MULTILINE_TYPES.includes(q.tipus)
    const isHtml   = q.tipus === 'HTML_CSS'
    const output   = outputs[q.id]
    const lang     = LANG_MAP[q.tipus] ?? 'plaintext'
    // Les imatges que l'enunciat ja mostra no es llisten com a fitxers adjunts
    const mostrades = idsReferenciats(q.enunciat)
    const fitxersAdjunts = (q.files ?? []).filter(f => !mostrades.has(f.id))
    const hasFiles = fitxersAdjunts.length > 0

    return (
      <div key={q.id} className="bg-white border rounded-xl p-6 space-y-3">
        <div className="flex items-start justify-between">
          <div>
            <span className="text-xs bg-brand-100 text-brand-700 px-2 py-0.5 rounded mr-2 font-mono font-semibold">{num}</span>
            <span className="text-xs text-gray-400">{q.tipus} · {q.punts} pts</span>
            {q.ambApunts && (
              <span className="ml-2 text-xs bg-sky-100 text-sky-800 px-2 py-0.5 rounded">📖 Pots fer servir apunts</span>
            )}
          </div>
        </div>
        <Md>{q.enunciat}</Md>

        {/* Fitxers de dades adjunts */}
        {hasFiles && (
          <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 flex flex-wrap gap-2 items-center">
            <span className="text-xs text-amber-700 font-medium">Arxius disponibles a <code className="bg-amber-100 px-1 rounded">/data/files/</code>:</span>
            {fitxersAdjunts.map(f => (
              <button key={f.id} type="button"
                onClick={() => downloadQuestionFile(f.id, f.filename)}
                className="text-xs text-blue-600 hover:underline flex items-center gap-1 bg-white border border-blue-200 rounded px-2 py-0.5">
                📎 {f.filename}
              </button>
            ))}
          </div>
        )}

        {/* Nota d'ajuda per Java */}
        {q.tipus === 'JAVA_PROG' && (
          <p className="text-xs text-gray-400 italic">La classe principal ha de dir-se <code>Main</code> i tenir el mètode <code>public static void main(String[] args)</code>.</p>
        )}

        {/* Editor de codi (scripts + Java) */}
        {isExec && (
          <div className="border rounded overflow-hidden">
            <Editor
              height={isML ? '300px' : '60px'}
              language={lang}
              value={answers[q.id] ?? ''}
              onChange={v => !submitted && handleChange(q.id, v ?? '')}
              options={{ fontSize: 13, minimap: { enabled: false }, readOnly: submitted,
                         lineNumbers: isML ? 'on' : 'off', scrollBeyondLastLine: false,
                         automaticLayout: true }}
              theme="vs-dark"
            />
          </div>
        )}

        {/* Editor HTML/CSS amb preview */}
        {isHtml && (
          <div className="space-y-2">
            <div className="border rounded overflow-hidden">
              <Editor
                height="250px"
                language="html"
                value={answers[q.id] ?? ''}
                onChange={v => !submitted && handleChange(q.id, v ?? '')}
                options={{ fontSize: 13, minimap: { enabled: false }, readOnly: submitted,
                           lineNumbers: 'on', scrollBeyondLastLine: false, automaticLayout: true }}
                theme="vs-dark"
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-gray-500">Previsualització en temps real:</span>
              <button
                onClick={() => setHtmlPreviews(prev => ({ ...prev, [q.id]: !prev[q.id] }))}
                className="text-xs text-brand-600 hover:underline">
                {htmlPreviews[q.id] ? 'Amagar' : 'Mostrar'} previsualització
              </button>
            </div>
            {htmlPreviews[q.id] && (
              <iframe
                srcDoc={answers[q.id] ?? ''}
                sandbox="allow-scripts"
                className="w-full border rounded bg-white"
                style={{ height: '300px' }}
                title={`preview-${q.id}`}
              />
            )}
          </div>
        )}

        {/* Respostes de text (SHORT, LONG, TEXT). Les de test només tenen les opcions: un text
            lliure hi substituiria la lletra triada */}
        {!isExec && !isHtml && q.tipus !== 'CHOICE' && q.tipus !== 'FILE_UPLOAD' && (
          <textarea
            rows={q.tipus === 'SHORT' ? 3 : 6}
            value={answers[q.id] ?? ''}
            onChange={e => !submitted && handleChange(q.id, e.target.value)}
            readOnly={submitted}
            placeholder="Escriu la teva resposta aquí..."
            className="w-full border rounded-lg px-3 py-2 text-sm resize-y focus:outline-none focus:ring-2 focus:ring-brand-400"
          />
        )}

        {/* Lliurament de fitxer (Word, Excel, Packet Tracer…) */}
        {q.tipus === 'FILE_UPLOAD' && (() => {
          const fitxer = fitxers[q.id]
          const formats = q.formatsPermesos ?? []
          const pujadaActiva = config?.pujadaFitxersActiva !== false
          return (
            <div className="border border-dashed border-gray-300 rounded-lg p-4 space-y-3 bg-gray-50">
              <p className="text-xs text-gray-600">
                Puja un fitxer {llistaFormats(formats)} · màxim {Math.round(MIDA_MAXIMA_FITXER / (1024 * 1024))} MB.
                {' '}Si en puges un altre, substitueix l'anterior.
              </p>
              {fitxer ? (
                <div className="flex flex-wrap items-center gap-3 bg-white border rounded-lg px-3 py-2">
                  <span className="text-lg" aria-hidden>📎</span>
                  <span className="text-sm font-medium text-gray-800 break-all">{fitxer.nom}</span>
                  <span className="text-xs text-gray-400">{formatMida(fitxer.mida)}</span>
                  <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">✓ Pujat</span>
                  <button type="button" onClick={() => session && descarregaFitxerResposta(session.id, q.id, fitxer.nom)}
                    className="text-xs text-brand-600 hover:underline ml-auto">Descarregar</button>
                  {!submitted && (
                    <button type="button" onClick={() => handleEsborraFitxer(q)} disabled={pujant === q.id}
                      className="text-xs text-red-600 hover:underline disabled:opacity-50">Esborrar</button>
                  )}
                </div>
              ) : (
                <p className="text-sm text-gray-400 italic">{submitted ? 'No has pujat cap fitxer.' : 'Encara no has pujat cap fitxer.'}</p>
              )}
              {!submitted && !pujadaActiva && (
                <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  La pujada de fitxers està desactivada. Consulta el professor.
                </p>
              )}
              {!submitted && pujadaActiva && (
                <label className={`inline-block text-sm px-4 py-2 rounded-lg cursor-pointer text-white
                  ${pujant === q.id ? 'bg-gray-400 cursor-wait' : 'bg-brand-600 hover:bg-brand-700'}`}>
                  {pujant === q.id ? 'Pujant…' : fitxer ? 'Substituir el fitxer' : 'Triar un fitxer'}
                  <input type="file" className="sr-only" disabled={pujant !== null}
                    accept={formats.map(f => '.' + f).join(',')}
                    onChange={e => {
                      const f = e.target.files?.[0]
                      e.target.value = ''
                      if (f) handlePuja(q, f)
                    }} />
                </label>
              )}
              {errorsFitxer[q.id] && (
                <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  {errorsFitxer[q.id]}
                </p>
              )}
            </div>
          )
        })()}

        {/* Opcions test */}
        {q.tipus === 'CHOICE' && q.choices && (
          <div className="space-y-2 mt-1">
            {shuffledChoices(q.choices, q.barrejarOpcions ? hashStr(session!.id + q.id) : null).map(({ letter, text }, i) => {
              const isSelected = answers[q.id] === letter
              return (
                <label key={letter}
                  className={`flex items-center gap-3 px-4 py-3 rounded-lg border cursor-pointer transition-colors ${
                    submitted ? 'cursor-default' : 'hover:bg-brand-50 hover:border-brand-300'
                  } ${isSelected ? 'border-brand-400 bg-brand-50' : 'border-gray-200'}`}>
                  <input type="radio" name={q.id} value={letter}
                    checked={isSelected}
                    onChange={() => !submitted && handleChange(q.id, letter)}
                    disabled={submitted}
                    className="accent-brand-600 w-4 h-4 flex-shrink-0" />
                  <span className="text-sm text-gray-800">{String.fromCharCode(97 + i)}) {text}</span>
                </label>
              )
            })}
          </div>
        )}

        {/* Botó executar (scripts + Java) */}
        {isExec && !submitted && (
          <button
            onClick={() => handleRun(q.id)}
            disabled={running === q.id || !answers[q.id]?.trim()}
            className="flex items-center gap-2 bg-gray-800 text-white text-xs px-4 py-1.5 rounded hover:bg-gray-700 disabled:opacity-50">
            {running === q.id ? '⏳ Executant…' : '▶ Executar'}
          </button>
        )}

        {/* Error de connexió / servidor */}
        {runErrors[q.id] && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">
            {runErrors[q.id]}
          </div>
        )}

        {/* Output d'execució */}
        {output && (
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs text-gray-500">Output:</span>
              <span className={`text-xs px-2 py-0.5 rounded ${output.succeeded ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                exit {output.exitCode} · {output.durationMs}ms
              </span>
            </div>
            <pre className="bg-gray-900 text-green-300 text-xs p-3 rounded overflow-auto max-h-48">
              {output.output?.trim() || '(sense output)'}
            </pre>
          </div>
        )}
      </div>
    )
  }

  const isAnswered = (q: Q) => {
    const a = answers[q.id]?.trim()
    return !!a && !(q.tipus === 'JAVA_PROG' && answers[q.id] === JAVA_STUB)
  }

  const handleSubmit = async () => {
    if (!session || !exam) return
    const senseResposta = exam.questions.filter(q => q.tipus !== 'SECTION' && !isAnswered(q)).length
    const avis = senseResposta > 0
      ? `Tens ${senseResposta} ${senseResposta === 1 ? 'pregunta' : 'preguntes'} sense resposta. `
      : ''
    if (!confirmaSenseFocus(`${avis}Enviar l'examen? No es podran modificar les respostes.`)) return
    setSubmit(true)
    await desaTot()
    try {
      await submitSession(session.id)
    } catch (err: any) {
      setSubmit(false)
      setSaveError(err?.response?.data?.error || 'No s\'ha pogut entregar l\'examen. Torna-ho a provar.')
      return
    }
    navigate(`/student/sessions/${session.id}/results`, { replace: true })
  }

  if (loadError) {
    const isWindow = loadError.includes('període') || loadError.includes('Comença') || loadError.includes('tancat')
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center px-4">
          <div className={`text-5xl`}>{isWindow ? '🕐' : '⚠️'}</div>
          <p className="text-xl font-semibold text-gray-800">
            {isWindow ? 'Accés no permès' : 'No s\'ha pogut carregar l\'examen'}
          </p>
          <p className="text-gray-600 max-w-md">{loadError}</p>
          {!isWindow && (
            <button onClick={() => window.location.reload()} className="text-brand-600 underline text-sm">Tornar a intentar</button>
          )}
        </div>
      </Layout>
    )
  }
  if (confirmRestart && exam && session) {
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-6 text-center px-4 max-w-md mx-auto">
          <div className="text-5xl">📋</div>
          <h2 className="text-xl font-semibold text-gray-800">{exam.title}</h2>
          <p className="text-gray-600">
            Ja has entregat aquest examen anteriorment. Si el tornes a fer, <strong>les respostes anteriors s'esborraran</strong>. El temps no es reinicia: continues amb el temps que et quedava.
          </p>
          <div className="flex gap-3 w-full">
            <button
              onClick={() => navigate(`/student/sessions/${session.id}/results`)}
              className="flex-1 border border-gray-300 text-gray-700 py-2 rounded-lg text-sm hover:bg-gray-50">
              Veure resultats
            </button>
            <button
              disabled={restarting}
              onClick={async () => {
                if (!examId) return
                setRestarting(true)
                try {
                  const fresh = await restartSession(examId)
                  setSession(fresh)
                  setAnswers({})
                  setOutputs({})
                  setOrderedQs(shuffleWithinSections(exam.questions, hashStr(fresh.id)))
                  avisosMostrats.current = { deu: false, un: false }
                  setDeadline(new Date(fresh.startedAt ?? Date.now()).getTime() + exam.durada * 60_000)
                  setConfirmRestart(false)
                } catch (err: any) {
                  setLoadError(err?.response?.data?.error || err?.message || 'Error reiniciant la sessió')
                  setConfirmRestart(false)
                } finally {
                  setRestarting(false)
                }
              }}
              className="flex-1 bg-brand-600 text-white py-2 rounded-lg text-sm hover:bg-brand-700 disabled:opacity-50">
              {restarting ? 'Reiniciant…' : 'Tornar a fer'}
            </button>
          </div>
        </div>
      </Layout>
    )
  }

  if (!exam || !session) return <Layout examMode><p className="text-gray-400 p-8">Carregant…</p></Layout>

  // Mode una pregunta per pantalla
  const paginat = exam.unaPreguntaPerPantalla
  const items: { q: Q; num: number; section: Q | null }[] = []
  {
    let section: Q | null = null
    for (const q of orderedQs) {
      if (q.tipus === 'SECTION') section = q
      else items.push({ q, num: items.length + 1, section })
    }
  }
  const nRespostes = items.filter(it => isAnswered(it.q)).length

  const goTo = (i: number) => {
    const next = Math.max(0, Math.min(i, items.length - 1))
    setCurrent(next)
    try { sessionStorage.setItem(`exam-pos-${session.id}`, String(next)) } catch { /* sense emmagatzematge */ }
    window.scrollTo({ top: 0 })
  }

  return (
    <Layout examMode>
      <div className="space-y-6">
        {/* Capçalera */}
        <div className="bg-brand-600 text-white rounded-xl p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-xl font-bold">{exam.title}</h1>
              <p className="text-brand-100 text-sm mt-1">{exam.durada} min · {exam.questions.filter(q => q.tipus !== 'SECTION').length} preguntes</p>
              {exam.instruccions && <p className="text-white/80 text-sm mt-2">{exam.instruccions}</p>}
              {authUser && (
                <div className="mt-3 inline-flex items-center gap-2 bg-white/20 rounded-lg px-3 py-1.5">
                  <span className="text-white/70 text-xs">Alumne:</span>
                  <span className="text-white font-semibold text-sm">{authUser.name}</span>
                  <span className="text-white/50 text-xs">({authUser.email})</span>
                </div>
              )}
            </div>
            {secondsLeft !== null && !submitted && (
              <div className={`text-right flex-shrink-0 rounded-lg px-3 py-2 ${secondsLeft < 300 ? 'bg-red-500/80' : 'bg-white/20'}`}>
                <p className="text-xs text-white/70 leading-none mb-0.5">Temps restant</p>
                <p className="text-2xl font-mono font-bold tabular-nums">
                  {String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:{String(secondsLeft % 60).padStart(2, '0')}
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Barra fixa: temps restant i estat del desament, sempre visibles */}
        {!submitted && secondsLeft !== null && (
          <div className="sticky top-0 z-20 -mx-2 px-4 py-2 rounded-b-xl bg-white/95 backdrop-blur border border-t-0 shadow-sm flex items-center justify-between gap-3 text-sm">
            <span className={`font-mono font-semibold tabular-nums ${secondsLeft < 300 ? 'text-red-700' : 'text-gray-800'}`}
              aria-label={`Temps restant: ${Math.floor(secondsLeft / 60)} minuts`}>
              ⏱ {String(Math.floor(secondsLeft / 60)).padStart(2, '0')}:{String(secondsLeft % 60).padStart(2, '0')}
            </span>
            <span aria-live="polite" className={`text-xs ${
              estatDesat === 'error' ? 'text-red-700 font-semibold' : estatDesat === 'desat' ? 'text-green-700' : 'text-gray-500'}`}>
              {estatDesat === 'pendent' && 'Canvis sense desar…'}
              {estatDesat === 'desant' && 'Desant…'}
              {estatDesat === 'desat' && '✓ Respostes desades'}
              {estatDesat === 'error' && '⚠ No desat: revisa la connexió'}
            </span>
          </div>
        )}

        {avisTemps && !submitted && (
          <div role="alert" className="bg-red-50 border border-red-300 rounded-xl px-4 py-3 flex items-center justify-between gap-3 text-sm text-red-800">
            <span>⏱ <strong>{avisTemps}</strong></span>
            <button onClick={() => setAvisTemps('')} aria-label="Tancar l'avís" className="text-red-400 hover:text-red-600 font-bold text-lg leading-none">×</button>
          </div>
        )}

        {saveError && (
          <div role="alert" className="bg-amber-50 border border-amber-300 rounded-xl px-4 py-3 text-sm text-amber-900">
            <strong>No s'ha pogut desar l'última resposta.</strong> {saveError}
          </div>
        )}

        {focusWarning && (
          <div className="bg-red-50 border border-red-300 rounded-xl px-4 py-3 flex items-center justify-between text-sm">
            <span className="text-red-700 font-medium">S'ha detectat una pèrdua de focus. Aquesta acció queda registrada.</span>
            <button onClick={() => setFocusWarning(false)} className="text-red-400 hover:text-red-600 ml-4 font-bold text-lg leading-none">×</button>
          </div>
        )}

        {/* Preguntes: totes seguides */}
        {!paginat && (() => {
          let num = 0
          return orderedQs.map(q => q.tipus === 'SECTION' ? renderSection(q) : renderQuestion(q, ++num))
        })()}

        {/* Preguntes: una per pantalla */}
        {paginat && items.length > 0 && (() => {
          const idx = Math.min(current, items.length - 1)
          const { q, num, section } = items[idx]
          return (
            <div className="space-y-4">
              <nav aria-label="Índex de preguntes" className="bg-white border rounded-xl p-3 flex flex-wrap items-center gap-1.5">
                {items.map((it, i) => {
                  const done = isAnswered(it.q)
                  return (
                    <button key={it.q.id} onClick={() => goTo(i)}
                      aria-current={i === idx ? 'step' : undefined}
                      aria-label={`Pregunta ${it.num}${done ? ', resposta' : ', sense resposta'}`}
                      className={`w-9 h-9 rounded-lg text-sm font-mono font-semibold border transition-colors ${
                        done ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'
                      } ${i === idx ? 'ring-2 ring-offset-1 ring-brand-500' : ''}`}>
                      {it.num}
                    </button>
                  )
                })}
                <span className="ml-auto text-xs text-gray-500">{nRespostes}/{items.length} respostes</span>
              </nav>

              {section && renderSection(section)}
              {renderQuestion(q, num)}

              <div className="flex items-center justify-between gap-3">
                <button onClick={() => goTo(idx - 1)} disabled={idx === 0}
                  className="border border-gray-300 text-gray-700 px-4 py-2 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-40">
                  ← Anterior
                </button>
                <span className="text-sm text-gray-500">Pregunta {num} de {items.length}</span>
                <button onClick={() => goTo(idx + 1)} disabled={idx === items.length - 1}
                  className="bg-brand-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-brand-700 disabled:opacity-40">
                  Següent →
                </button>
              </div>
            </div>
          )
        })()}

        {!submitted && (
          <button onClick={handleSubmit}
            className="w-full bg-brand-600 text-white py-3 rounded-xl font-medium hover:bg-brand-700">
            Enviar examen
          </button>
        )}
      </div>
    </Layout>
  )
}

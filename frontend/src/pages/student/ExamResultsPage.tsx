import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import Md from '../../components/Md'
import { getSession } from '../../api/sessions'
import { getExam } from '../../api/exams'
import type { Session, Exam } from '../../types'
import { descarregaFitxerResposta } from '../../api/respostaFitxer'
import { formatMida } from '../../utils/fitxers'
import { notaTotal, notaPerRa, puntsPregunta, puntsMaxims, sobreDeu } from '../../notes'

export default function ExamResultsPage() {
  const { sessionId } = useParams<{ sessionId: string }>()
  const [session, setSession] = useState<Session | null>(null)
  const [exam, setExam]       = useState<Exam | null>(null)

  useEffect(() => {
    if (!sessionId) return
    getSession(sessionId).then(s => {
      setSession(s)
      return getExam(s.examId)
    }).then(setExam)
  }, [sessionId])

  if (!session || !exam) {
    return (
      <Layout examMode>
        <div className="flex items-center justify-center min-h-[60vh]">
          <div className="w-8 h-8 border-4 border-brand-300 border-t-brand-600 rounded-full animate-spin" />
        </div>
      </Layout>
    )
  }

  if (!session.notesVisibles) {
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
          <div className="text-4xl">⏳</div>
          <h2 className="text-xl font-semibold text-gray-700">{exam.title}</h2>
          <p className="text-gray-500 text-sm max-w-sm">
            L'examen ha estat entregat correctament. Les notes estaran disponibles quan el professor les publiqui.
          </p>
        </div>
      </Layout>
    )
  }

  const answerMap = Object.fromEntries(session.answers.map(a => [a.questionId, a]))

  // Cada pregunta compta una sola vegada: nota revisada o, si no n'hi ha, la proposta; bonus = punts sencers
  const { total } = notaTotal(exam.questions, session.answers)
  const totalMax  = puntsMaxims(exam.questions)
  const notaDeu   = totalMax !== 10 ? sobreDeu(total, totalMax) : null

  return (
    <Layout examMode>
      <div className="space-y-6 max-w-3xl mx-auto">

        {/* Resum */}
        <div className="bg-brand-600 text-white rounded-xl p-6">
          <h1 className="text-xl font-bold">{exam.title}</h1>
          <p className="text-brand-100 text-sm mt-1">Examen entregat</p>
          <div className="mt-4 flex gap-6">
            <div>
              <p className="text-xs text-white/70">Nota</p>
              <p className="text-3xl font-bold tabular-nums">
                {total.toFixed(2)} <span className="text-base font-normal text-white/70">/ {totalMax}</span>
              </p>
              {notaDeu != null && <p className="text-sm text-white/80 tabular-nums">{notaDeu.toFixed(2)} sobre 10</p>}
            </div>
          </div>
        </div>

        {/* Desglossament per RA */}
        {(() => {
          const entries = notaPerRa(exam.questions, session.answers)
            .map(([ra, g]) => [ra, { ...g, hasScore: true }] as const)
          if (entries.length === 0) return null
          return (
            <div className="bg-white border border-indigo-100 rounded-xl px-5 py-4 space-y-3">
              <p className="text-xs font-semibold text-indigo-600 uppercase tracking-wide">Nota per Resultat d'Aprenentatge</p>
              <div className="flex flex-wrap gap-4">
                {entries.map(([ra, { pts, scored, hasScore }]) => (
                  <div key={ra} className="text-center min-w-[80px]">
                    <div className="text-xs font-semibold text-indigo-700 mb-1">{ra}</div>
                    {hasScore
                      ? <div className={`text-lg font-bold tabular-nums ${scored / pts >= 0.5 ? 'text-green-600' : 'text-red-500'}`}>
                          {scored.toFixed(2)}<span className="text-xs font-normal text-gray-400">/{pts}</span>
                        </div>
                      : <div className="text-xs text-gray-400">Pendent</div>
                    }
                  </div>
                ))}
              </div>
            </div>
          )
        })()}

        {/* Detall per pregunta */}
        {exam.questions.map(q => {
          if (q.tipus === 'SECTION') {
            return (
              <div key={q.id} className="pt-2 pb-1">
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-px bg-brand-200" />
                  <span className="text-brand-700 font-semibold text-sm px-2">{q.enunciat}</span>
                  <div className="flex-1 h-px bg-brand-200" />
                </div>
              </div>
            )
          }

          const answer = answerMap[q.id]
          const score  = puntsPregunta(q, answer)
          const isScored = score != null

          return (
            <div key={q.id} className="bg-white border rounded-xl p-6 space-y-3">
              <div className="flex items-start justify-between gap-4">
                <div className="flex items-center gap-2">
                  <span className="text-xs bg-brand-100 text-brand-700 px-2 py-0.5 rounded">{q.ordre}</span>
                  <span className="text-xs text-gray-400">{q.tipus}</span>
                  {q.anulada && <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">Bonus: tothom té els punts</span>}
                </div>
                <div className="text-right flex-shrink-0">
                  {isScored ? (
                    <span className={`text-sm font-semibold ${score === q.punts ? 'text-green-600' : score === 0 ? 'text-red-500' : 'text-orange-500'}`}>
                      {(score as number).toFixed(2)} / {q.punts}
                    </span>
                  ) : (
                    <span className="text-xs text-gray-400">Pendent de correcció · {q.punts} pts</span>
                  )}
                </div>
              </div>

              <Md>{q.enunciat}</Md>

              {q.tipus === 'FILE_UPLOAD' && answer?.fitxerNom && (
                <div className="bg-gray-50 border rounded-lg px-4 py-3 flex flex-wrap items-center gap-3">
                  <span aria-hidden>📎</span>
                  <span className="text-sm font-medium text-gray-800 break-all">{answer.fitxerNom}</span>
                  <span className="text-xs text-gray-400">{formatMida(answer.fitxerMida ?? 0)}</span>
                  <button type="button"
                    onClick={() => descarregaFitxerResposta(session.id, q.id, answer.fitxerNom ?? 'lliurament')}
                    className="text-xs text-brand-600 hover:underline ml-auto">Descarregar</button>
                </div>
              )}

              {q.tipus !== 'FILE_UPLOAD' && answer?.contingut && (
                <div className="bg-gray-50 border rounded-lg px-4 py-3">
                  <p className="text-xs text-gray-400 mb-1">La teva resposta</p>
                  <pre className="text-sm text-gray-800 whitespace-pre-wrap font-sans">
                    {q.tipus === 'CHOICE'
                      // Les opcions es mostren barrejades a cada alumne: la lletra desada no és la que va veure
                      ? (q.choices?.find(c => c.charAt(0) === answer.contingut)?.replace(/^[a-zA-Z]\)\s*/, '') ?? answer.contingut)
                      : answer.contingut}
                  </pre>
                </div>
              )}

              {answer?.executionOutput && (
                <div>
                  <p className="text-xs text-gray-400 mb-1">Output d'execució</p>
                  <pre className="bg-gray-900 text-green-300 text-xs p-3 rounded overflow-auto max-h-32">
                    {answer.executionOutput}
                  </pre>
                </div>
              )}

              {!answer?.contingut && !answer?.fitxerNom && !q.anulada && (
                <p className="text-sm text-gray-400 italic">Sense resposta</p>
              )}

              {answer?.comentari && (
                <div className="bg-brand-50 border border-brand-200 rounded-lg px-4 py-3">
                  <p className="text-xs text-brand-700 font-medium mb-1">Comentari del professor</p>
                  <p className="text-sm text-gray-800 whitespace-pre-wrap">{answer.comentari}</p>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </Layout>
  )
}

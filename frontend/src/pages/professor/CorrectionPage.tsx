import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import Md from '../../components/Md'
import { getSessionsByExam, getSession } from '../../api/sessions'
import { getExam, runAnswer, setManualScore, setComment, patchQuestion, acceptProposal, acceptAllProposals } from '../../api/exams'
import type { Exam, Session, Answer, Question } from '../../types'
import { notaTotal, notaPerRa, puntsMaxims, sobreDeu } from '../../notes'
import { useLlindarFocus } from '../../context/ConfiguracioContext'
import RespostaCorreccio, { Referencia, isPendent } from '../../components/correccio/RespostaCorreccio'
import type { AccionsCorreccio } from '../../components/correccio/RespostaCorreccio'
import ExportarExamen from '../../components/ExportarExamen'
import ImportarRevisioIa from '../../components/ImportarRevisioIa'

type Tab = 'preguntes' | 'perPregunta' | 'alumnes'
type FiltreAlumnes = 'tots' | 'pendents' | 'revisats' | 'noEntregats'
type FiltreRespostes = 'pendents' | 'totes'

const fmtPunts = (n: number) => Number(n.toFixed(2)).toString()

function totalScore(session: Session, questions: Question[]): number {
  return notaTotal(questions, session.answers).total
}

function pendents(session: Session, questions: Question[]): Answer[] {
  return session.answers.filter(a => isPendent(session, questions.find(q => q.id === a.questionId), a))
}

const perNom = (a: Session, b: Session) => a.studentName.localeCompare(b.studentName, 'ca')

export default function CorrectionPage() {
  const { examId }              = useParams<{ examId: string }>()
  const [tab, setTab]           = useState<Tab>('preguntes')
  const [exam, setExam]         = useState<Exam | null>(null)
  const [sessions, setSessions] = useState<Session[]>([])
  const [selected, setSelected] = useState<Session | null>(null)
  const [editQ, setEditQ]       = useState<Question | null>(null)
  const [editAnswer, setEditAnswer] = useState('')
  const [avis, setAvis] = useState('')
  const llindarFocus = useLlindarFocus()
  // Filtres
  const [cercaAlumne, setCercaAlumne]       = useState('')
  const [filtreAlumnes, setFiltreAlumnes]   = useState<FiltreAlumnes>('tots')
  const [nomesPendentsAlumne, setNomesPendentsAlumne] = useState(false)
  const [preguntaId, setPreguntaId]         = useState<string | null>(null)
  const [filtreRespostes, setFiltreRespostes] = useState<FiltreRespostes>('pendents')

  const reload = async () => {
    if (!examId) return
    const [e, ss] = await Promise.all([getExam(examId), getSessionsByExam(examId)])
    setExam(e); setSessions(ss)
    if (selected) {
      const full = await getSession(selected.id)
      setSelected(full)
    }
  }

  useEffect(() => { reload() }, [examId])

  const selectSession = async (s: Session) => {
    const full = await getSession(s.id)
    setSelected(full)
  }

  /** Actualitza una resposta tant al detall de l'alumne com al llistat (totals i pendents). */
  const updateAnswer = (sessionId: string, answerId: string, patch: Partial<Answer>) => {
    const apply = (s: Session) => s.id !== sessionId ? s : {
      ...s, answers: s.answers.map(a => a.id === answerId ? { ...a, ...patch } : a)
    }
    setSelected(prev => prev ? apply(prev) : prev)
    setSessions(prev => prev.map(apply))
  }

  const refreshSession = async (sessionId: string) => {
    const full = await getSession(sessionId)
    setSelected(prev => prev?.id === full.id ? full : prev)
    setSessions(prev => prev.map(s => s.id === full.id ? { ...s, answers: full.answers } : s))
  }

  const errorDe = (err: any, perDefecte: string) => err?.response?.data?.error || perDefecte

  const accions: AccionsCorreccio = {
    onScore: async (session, answer, score) => {
      setAvis('')
      try {
        await setManualScore(answer.id, score)
        updateAnswer(session.id, answer.id, { manualScore: score })
      } catch (err: any) {
        setAvis(errorDe(err, 'No s\'ha pogut desar la puntuació'))
      }
    },
    onAccept: async (session, answer) => {
      setAvis('')
      try {
        const updated = await acceptProposal(answer.id)
        updateAnswer(session.id, answer.id, { manualScore: updated.manualScore })
      } catch (err: any) {
        setAvis(errorDe(err, 'No s\'ha pogut acceptar la proposta'))
      }
    },
    onRun: async (session, answer) => {
      try {
        await runAnswer(answer.id)
        // la nova execució recalcula la proposta i els motius
        await refreshSession(session.id)
      } catch (err: any) {
        setAvis(errorDe(err, 'No s\'ha pogut executar'))
      }
    },
    onComment: async (session, answer, comentari) => {
      try {
        const updated = await setComment(answer.id, comentari)
        updateAnswer(session.id, answer.id, { comentari: updated.comentari })
      } catch (err: any) {
        setAvis(errorDe(err, 'No s\'ha pogut desar el comentari'))
      }
    },
  }

  const handleAcceptAll = async (sessionId?: string) => {
    if (!examId) return
    const { acceptades } = await acceptAllProposals(examId, sessionId)
    setAvis(acceptades === 1 ? '1 proposta acceptada' : `${acceptades} propostes acceptades`)
    reload()
  }

  /** Bonus: tothom té la pregunta bé (punts sencers), hagi respost o no. Es pot desfer. */
  const handleBonus = async (q: Question) => {
    if (!examId) return
    try {
      await patchQuestion(examId, q.id, { anulada: !q.anulada })
      setAvis(q.anulada
        ? `S'ha tret el bonus de la pregunta ${q.ordre}: torna a comptar la nota de cada alumne.`
        : `Pregunta ${q.ordre} marcada com a bonus: tothom en té els ${q.punts} punts.`)
      reload()
    } catch (err: any) {
      setAvis(errorDe(err, 'No s\'ha pogut canviar el bonus'))
    }
  }

  const saveEditAnswer = async () => {
    if (!examId || !editQ || !editAnswer.trim()) return
    await patchQuestion(examId, editQ.id, { correctChoice: editAnswer.trim() })
    setEditQ(null); reload()
  }

  const obreCorreccioPregunta = (q: Question) => {
    setPreguntaId(q.id)
    setTab('perPregunta')
  }

  const questions = exam?.questions.filter(q => q.tipus !== 'SECTION') ?? []
  const sections  = exam?.questions ?? []
  const totalPendents = sessions.reduce((n, s) => n + pendents(s, questions).length, 0)
  const acceptables   = sessions.reduce((n, s) =>
    n + pendents(s, questions).filter(a => a.autoScore != null).length, 0)

  const pendentsPerPregunta = (q: Question) =>
    sessions.filter(s => isPendent(s, q, s.answers.find(a => a.questionId === q.id))).length

  // ── Vista per alumne: filtre del llistat ──
  const alumnesFiltrats = useMemo(() => {
    const cerca = cercaAlumne.trim().toLowerCase()
    return [...sessions].sort(perNom).filter(s => {
      if (cerca && !s.studentName.toLowerCase().includes(cerca)) return false
      const n = pendents(s, questions).length
      switch (filtreAlumnes) {
        case 'pendents':    return n > 0
        case 'revisats':    return s.status === 'SUBMITTED' && n === 0
        case 'noEntregats': return s.status !== 'SUBMITTED'
        default:            return true
      }
    })
  }, [sessions, cercaAlumne, filtreAlumnes, questions])

  // ── Vista per pregunta ──
  const pregunta = questions.find(q => q.id === preguntaId) ?? questions[0]
  const idxPregunta = pregunta ? questions.indexOf(pregunta) : -1

  const tabs: { id: Tab; label: string }[] = [
    { id: 'preguntes',   label: `Preguntes (${questions.length})` },
    { id: 'perPregunta', label: 'Corregir per pregunta' },
    { id: 'alumnes',     label: `Alumnes (${sessions.length})` },
  ]

  return (
    <Layout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-brand-700">{exam?.title ?? 'Correccions'}</h1>
        <div className="flex flex-wrap items-center gap-3">
          {totalPendents > 0 && (
            <span className="text-xs bg-amber-100 text-amber-800 px-2 py-1 rounded font-medium">
              {totalPendents} pendents de revisar
            </span>
          )}
          {acceptables > 0 && (
            <button onClick={() => handleAcceptAll()}
              title="Converteix en nota revisada totes les notes proposades que encara no s'han revisat"
              className="text-xs bg-amber-600 text-white px-3 py-1.5 rounded-lg hover:bg-amber-700">
              Acceptar totes les propostes ({acceptables})
            </button>
          )}
          {examId && (
            <Link to={`/professor/exams/${examId}/stats`}
              className="text-xs border border-brand-600 text-brand-600 px-3 py-1.5 rounded-lg hover:bg-brand-50">
              Estadístiques
            </Link>
          )}
          {examId && (
            <Link to={`/professor/exams/${examId}/copies`}
              className="text-xs border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50">
              Possibles còpies
            </Link>
          )}
          {examId && (
            <ExportarExamen examId={examId} titol={exam?.title ?? 'examen'}
              teFitxers={!!exam?.questions?.some(q => q.tipus === 'FILE_UPLOAD')} />
          )}
          {examId && (
            <ImportarRevisioIa examId={examId} onAplicat={m => { setAvis(m); reload() }} />
          )}
          <div role="tablist" className="flex rounded-lg overflow-hidden border border-brand-200 text-sm">
            {tabs.map(t => (
              <button key={t.id} role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
                className={`px-4 py-2 transition-colors ${tab === t.id ? 'bg-brand-600 text-white' : 'text-brand-600 hover:bg-brand-50'}`}>
                {t.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {avis && (
        <div role="status" className="mb-4 bg-brand-50 border border-brand-200 text-brand-800 text-sm rounded-lg px-4 py-2 flex justify-between">
          <span>{avis}</span>
          <button onClick={() => setAvis('')} aria-label="Tancar l'avís" className="text-brand-500 hover:text-brand-700">✕</button>
        </div>
      )}

      {/* ── TAB PREGUNTES: resum ── */}
      {tab === 'preguntes' && (
        <div className="space-y-2">
          {sections.map(q => {
            if (q.tipus === 'SECTION') {
              return (
                <div key={q.id} className="pt-4 pb-1">
                  <div className="flex items-center gap-3">
                    <div className="flex-1 h-px bg-brand-200" />
                    <span className="text-brand-700 font-semibold text-sm px-2">{q.enunciat}</span>
                    <div className="flex-1 h-px bg-brand-200" />
                  </div>
                </div>
              )
            }

            const answered = sessions.filter(s =>
              s.answers.some(a => a.questionId === q.id && a.contingut)
            ).length
            const correct = q.tipus === 'CHOICE'
              ? sessions.filter(s =>
                  s.answers.some(a => a.questionId === q.id &&
                    a.contingut?.trim().toLowerCase() === q.correctChoice?.trim().toLowerCase())
                ).length
              : null
            const nPendents = pendentsPerPregunta(q)

            return (
              <div key={q.id} className={`bg-white border rounded-xl p-4 flex items-start gap-4 ${q.anulada ? 'border-green-200' : ''}`}>
                <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded font-medium shrink-0 mt-0.5">
                  {q.ordre}
                </span>
                <div className="flex-1 min-w-0">
                  <Md className="text-sm text-gray-800 line-clamp-2">{q.enunciat}</Md>
                  <div className="flex items-center gap-3 mt-1 text-xs text-gray-400 flex-wrap">
                    <span>{q.punts} pts</span>
                    {q.ra && <span className="bg-indigo-100 text-indigo-700 px-1.5 py-0.5 rounded font-medium">{q.ra}</span>}
                    {q.dificultat && <span className={`px-1.5 py-0.5 rounded font-medium ${
                      q.dificultat === 'baixa' ? 'bg-green-100 text-green-700' :
                      q.dificultat === 'mitjana' ? 'bg-amber-100 text-amber-700' : 'bg-red-100 text-red-700'
                    }`}>{q.dificultat}</span>}
                    <span>{answered}/{sessions.length} respostes</span>
                    {correct !== null && (
                      <span className="text-green-600">{correct}/{sessions.length} correctes</span>
                    )}
                    {nPendents > 0 && <span className="text-amber-700 font-medium">{nPendents} pendents</span>}
                    {q.anulada && <span className="bg-green-100 text-green-700 px-1.5 py-0.5 rounded font-semibold">BONUS · tothom la té bé</span>}
                    {q.tipus === 'CHOICE' && !q.anulada && (
                      <span className="text-brand-600">Correcta: <strong>{q.correctChoice?.toUpperCase()}</strong></span>
                    )}
                  </div>
                </div>
                <div className="flex gap-2 shrink-0">
                  <button onClick={() => obreCorreccioPregunta(q)}
                    className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700">
                    Corregir
                  </button>
                  {q.tipus === 'CHOICE' && !q.anulada && (
                    <button onClick={() => { setEditQ(q); setEditAnswer(q.correctChoice ?? '') }}
                      className="text-xs border border-brand-300 text-brand-600 px-3 py-1 rounded hover:bg-brand-50">
                      Canviar resposta
                    </button>
                  )}
                  <button onClick={() => handleBonus(q)}
                    title={q.anulada
                      ? 'Torna a comptar la nota que té cada alumne en aquesta pregunta'
                      : 'Tothom tindrà aquesta pregunta bé (punts sencers), l\'hagi resposta o no'}
                    className={`text-xs px-3 py-1 rounded border ${
                      q.anulada
                        ? 'border-gray-300 text-gray-600 hover:bg-gray-50'
                        : 'border-green-300 text-green-700 hover:bg-green-50'
                    }`}>
                    {q.anulada ? 'Treure bonus' : 'Bonus'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── TAB CORREGIR PER PREGUNTA ── */}
      {tab === 'perPregunta' && pregunta && (() => {
        const entregades = [...sessions].filter(s => s.status === 'SUBMITTED').sort(perNom)
        const files = entregades
          .map(s => ({ s, a: s.answers.find(a => a.questionId === pregunta.id) }))
          .filter(({ s, a }) => filtreRespostes === 'totes' || isPendent(s, pregunta, a))
        const nPendents = pendentsPerPregunta(pregunta)
        return (
          <div className="space-y-4">
            <div className="bg-white border rounded-xl p-4 space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <button onClick={() => setPreguntaId(questions[idxPregunta - 1]?.id ?? pregunta.id)}
                  disabled={idxPregunta <= 0} aria-label="Pregunta anterior"
                  className="border border-gray-300 rounded px-2 py-1 text-sm disabled:opacity-40 hover:bg-gray-50">←</button>
                <label className="flex-1 min-w-[12rem]">
                  <span className="sr-only">Pregunta</span>
                  <select value={pregunta.id} onChange={e => setPreguntaId(e.target.value)}
                    className="w-full border rounded-lg px-3 py-1.5 text-sm">
                    {questions.map(q => {
                      const n = pendentsPerPregunta(q)
                      return (
                        <option key={q.id} value={q.id}>
                          {q.ordre}. {q.enunciat.replace(/\s+/g, ' ').slice(0, 70)} {n > 0 ? `— ${n} pendents` : ''}
                        </option>
                      )
                    })}
                  </select>
                </label>
                <button onClick={() => setPreguntaId(questions[idxPregunta + 1]?.id ?? pregunta.id)}
                  disabled={idxPregunta >= questions.length - 1} aria-label="Pregunta següent"
                  className="border border-gray-300 rounded px-2 py-1 text-sm disabled:opacity-40 hover:bg-gray-50">→</button>
                <div role="radiogroup" aria-label="Quines respostes es mostren" className="flex rounded-lg overflow-hidden border border-gray-300 text-xs">
                  {(['pendents', 'totes'] as FiltreRespostes[]).map(f => (
                    <button key={f} role="radio" aria-checked={filtreRespostes === f} onClick={() => setFiltreRespostes(f)}
                      className={`px-3 py-1.5 ${filtreRespostes === f ? 'bg-gray-800 text-white' : 'text-gray-600 hover:bg-gray-50'}`}>
                      {f === 'pendents' ? `Pendents (${nPendents})` : `Totes (${entregades.length})`}
                    </button>
                  ))}
                </div>
              </div>
              <Md className="text-sm text-gray-800">{pregunta.enunciat}</Md>
              <p className="text-xs text-gray-500">{pregunta.tipus} · {pregunta.punts} pts{pregunta.anulada ? ' · BONUS: tothom la té bé' : ''}</p>
              {(pregunta.modelResposta || pregunta.claus) && <Referencia q={pregunta} oberta />}
              {pregunta.tipus !== 'CHOICE' && (
                <p className="text-xs text-gray-400">
                  Teclat: escriu la nota i prem <kbd className="border rounded px-1">Intro</kbd> per desar-la i passar a la següent.
                  Amb la casella buida, <kbd className="border rounded px-1">Intro</kbd> accepta la proposta.
                </p>
              )}
            </div>

            {files.length === 0 && (
              <p className="text-sm text-gray-500 bg-white border rounded-xl p-4">
                {filtreRespostes === 'pendents' ? '✓ No queda cap resposta pendent d\'aquesta pregunta.' : 'Encara no hi ha cap examen entregat.'}
              </p>
            )}
            {files.map(({ s, a }) => a ? (
              <RespostaCorreccio key={a.id} q={pregunta} answer={a} session={s} mostrarReferencia={false}
                titol={<span className="font-medium text-sm text-gray-800">{s.studentName}</span>}
                {...accions} />
            ) : (
              <div key={s.id} className="bg-white border rounded-xl px-4 py-2 text-sm flex justify-between">
                <span className="font-medium text-gray-800">{s.studentName}</span>
                <span className="text-gray-400 italic text-xs">sense resposta (0 punts)</span>
              </div>
            ))}
          </div>
        )
      })()}

      {/* ── TAB ALUMNES ── */}
      {tab === 'alumnes' && (
        <div className="flex flex-col md:flex-row gap-6">
          {/* Llistat alumnes */}
          <div className="md:w-60 shrink-0 space-y-2">
            <input type="search" value={cercaAlumne} onChange={e => setCercaAlumne(e.target.value)}
              placeholder="Cerca alumne…" aria-label="Cerca alumne"
              className="w-full border rounded-lg px-3 py-1.5 text-sm" />
            <select value={filtreAlumnes} onChange={e => setFiltreAlumnes(e.target.value as FiltreAlumnes)}
              aria-label="Filtra alumnes" className="w-full border rounded-lg px-3 py-1.5 text-sm">
              <option value="tots">Tots</option>
              <option value="pendents">Amb respostes pendents</option>
              <option value="revisats">Revisats del tot</option>
              <option value="noEntregats">No entregats</option>
            </select>
            <div className="space-y-1">
              {alumnesFiltrats.length === 0 && <p className="text-xs text-gray-400 px-1">Cap alumne</p>}
              {alumnesFiltrats.map(s => {
                const total = totalScore(s, questions)
                const nPendents = pendents(s, questions).length
                return (
                  <button key={s.id} onClick={() => selectSession(s)}
                    className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${selected?.id === s.id ? 'bg-brand-100 text-brand-700 font-medium' : 'hover:bg-gray-100'}`}>
                    <div className="flex items-center justify-between">
                      <span className="truncate">{s.studentName}</span>
                      <div className="flex items-center gap-1 shrink-0 ml-1">
                        {s.focusLossCount > 0 && (
                          <span className={`text-xs px-1 rounded font-semibold ${s.focusLossCount >= llindarFocus ? 'bg-red-100 text-red-700' : 'bg-yellow-100 text-yellow-700'}`}>
                            ⚠{s.focusLossCount}
                          </span>
                        )}
                        <span className="text-xs text-brand-600 font-semibold">{total.toFixed(2)}</span>
                      </div>
                    </div>
                    <div className="text-xs text-gray-400 flex items-center gap-2">
                      <span>{s.status === 'SUBMITTED' ? 'Entregat' : s.startedAt ? 'En curs' : 'No començat'}</span>
                      {nPendents > 0 && <span className="text-amber-700">· {nPendents} pendents</span>}
                    </div>
                  </button>
                )
              })}
            </div>
          </div>

          {/* Detall alumne */}
          <div className="flex-1 min-w-0 space-y-3">
            {!selected && <p className="text-gray-400 text-sm">Selecciona un alumne</p>}

            {selected && (
              <>
                <div className="bg-brand-50 border border-brand-200 rounded-xl px-5 py-3 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="font-semibold text-brand-800">{selected.studentName}</p>
                    {selected.focusLossCount > 0 && (
                      <p className={`text-xs mt-0.5 ${selected.focusLossCount >= llindarFocus ? 'text-red-600' : 'text-yellow-600'}`}>
                        ⚠ {selected.focusLossCount} pèrdues de focus
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-4">
                    {(() => {
                      const ps = pendents(selected, questions)
                      const nAcceptables = ps.filter(a => a.autoScore != null).length
                      if (ps.length === 0) return <span className="text-xs text-green-700">✓ Tot revisat</span>
                      return (
                        <div className="text-right space-y-1">
                          <p className="text-xs text-amber-700">{ps.length} pendents de revisar</p>
                          {nAcceptables > 0 && (
                            <button onClick={() => handleAcceptAll(selected.id)}
                              className="text-xs border border-amber-400 text-amber-800 px-2 py-0.5 rounded hover:bg-amber-100">
                              Acceptar les propostes ({nAcceptables})
                            </button>
                          )}
                        </div>
                      )
                    })()}
                    <div className="text-right">
                      <p className="text-2xl font-bold text-brand-700">{totalScore(selected, questions).toFixed(2)}</p>
                      <p className="text-xs text-gray-400">
                        / {fmtPunts(puntsMaxims(questions))} pts{pendents(selected, questions).length > 0 ? ' (provisional)' : ''}
                      </p>
                      {puntsMaxims(questions) !== 10 && (() => {
                        const n = sobreDeu(totalScore(selected, questions), puntsMaxims(questions))
                        return n != null && <p className="text-xs text-gray-500">{n.toFixed(2)} sobre 10</p>
                      })()}
                    </div>
                  </div>
                </div>

                {/* Desglossament per RA */}
                {(() => {
                  const entries = notaPerRa(questions, selected.answers)
                  if (entries.length === 0) return null
                  return (
                    <div className="bg-white border border-indigo-100 rounded-xl px-4 py-3 space-y-2">
                      <p className="text-xs font-semibold text-indigo-600 uppercase tracking-wide">Nota per RA</p>
                      <div className="flex flex-wrap gap-3">
                        {entries.map(([ra, { pts, scored }]) => (
                          <div key={ra} className="flex items-center gap-1.5">
                            <span className="bg-indigo-100 text-indigo-700 text-xs px-2 py-0.5 rounded font-medium">{ra}</span>
                            <span className="text-sm font-semibold text-gray-700">{scored.toFixed(2)}</span>
                            <span className="text-xs text-gray-400">/ {pts.toFixed(2)}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )
                })()}

                <div className="flex flex-wrap items-center justify-between gap-2">
                  <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                    <input type="checkbox" checked={nomesPendentsAlumne}
                      onChange={e => setNomesPendentsAlumne(e.target.checked)} className="accent-brand-600" />
                    Mostra només les respostes pendents
                  </label>
                  <p className="text-xs text-gray-400">
                    <kbd className="border rounded px-1">Intro</kbd> desa la nota i passa a la següent; buida, accepta la proposta.
                  </p>
                </div>

                {sections.map(q => {
                  if (q.tipus === 'SECTION') {
                    if (nomesPendentsAlumne) return null
                    return (
                      <div key={q.id} className="flex items-center gap-3 pt-2">
                        <div className="flex-1 h-px bg-brand-200" />
                        <span className="text-brand-700 font-semibold text-xs px-2">{q.enunciat}</span>
                        <div className="flex-1 h-px bg-brand-200" />
                      </div>
                    )
                  }
                  const answer = selected.answers.find(a => a.questionId === q.id)
                  if (nomesPendentsAlumne && !isPendent(selected, q, answer)) return null
                  return (
                    <RespostaCorreccio key={q.id} q={q} answer={answer} session={selected}
                      titol={<>
                        <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded shrink-0">{q.ordre}</span>
                        <Md className="text-sm text-gray-800">{q.enunciat}</Md>
                      </>}
                      {...accions} />
                  )
                })}
                {nomesPendentsAlumne && pendents(selected, questions).length === 0 && (
                  <p className="text-sm text-green-700 bg-white border rounded-xl p-4">✓ Aquest alumne no té cap resposta pendent.</p>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* Modal canviar resposta correcta */}
      {editQ && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" role="dialog" aria-modal="true" aria-labelledby="canviar-resposta">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm p-6 space-y-4">
            <h2 id="canviar-resposta" className="font-semibold text-gray-800">Canviar resposta correcta</h2>
            <p className="text-sm text-gray-500 line-clamp-2">{editQ.enunciat}</p>
            <div className="space-y-2">
              {editQ.choices?.map(choice => {
                const letter = choice.charAt(0)
                return (
                  <label key={letter}
                    className={`flex items-center gap-3 px-4 py-2.5 rounded-lg border cursor-pointer transition-colors ${
                      editAnswer === letter ? 'border-brand-400 bg-brand-50' : 'border-gray-200 hover:bg-gray-50'
                    }`}>
                    <input type="radio" name="new-answer" value={letter}
                      checked={editAnswer === letter}
                      onChange={() => setEditAnswer(letter)}
                      className="accent-brand-600" />
                    <span className="text-sm">{choice}</span>
                  </label>
                )
              })}
            </div>
            <div className="flex gap-3 justify-end">
              <button onClick={() => setEditQ(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
              <button onClick={saveEditAnswer}
                className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700">
                Guardar i re-corregir
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}

import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import Md from '../../components/Md'
import { getExam, updateExamSettings, patchQuestion } from '../../api/exams'
import { uploadQuestionFile, deleteQuestionFile, downloadQuestionFile } from '../../api/questionFiles'
import type { Exam, Question, QuestionFile, QuestionType } from '../../types'

const DIF_COLORS: Record<string, string> = {
  baixa:   'bg-green-100 text-green-700',
  mitjana: 'bg-amber-100 text-amber-700',
  alta:    'bg-red-100 text-red-700',
}

const SCRIPT_TYPES: QuestionType[] = ['BASH_CMD', 'PS_CMD', 'BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
const MULTILINE_TYPES: QuestionType[] = ['BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
const TYPE_LABEL: Partial<Record<QuestionType, string>> = {
  TEXT: 'Resposta llarga', SHORT: 'Resposta curta', LONG: 'Resposta llarga',
  BASH_CMD: 'Comanda Bash', PS_CMD: 'Comanda PowerShell',
  BASH_SCRIPT: 'Script Bash', PS_SCRIPT: 'Script PowerShell',
  JAVA_PROG: 'Programa Java', HTML_CSS: 'HTML/CSS',
}

type RaEdit = { ra: string; dif: string }

export default function ExamPreviewPage() {
  const { examId } = useParams<{ examId: string }>()
  const [exam, setExam] = useState<Exam | null>(null)
  const [showAnswers, setShowAnswers] = useState(false)
  const [draftAnswers, setDraftAnswers] = useState<Record<string, string>>({})
  const [penalEdit, setPenalEdit] = useState(false)
  const [penalValue, setPenalValue] = useState(0)
  const [dadesEdit, setDadesEdit] = useState(false)
  const [titolValue, setTitolValue] = useState('')
  const [duradaValue, setDuradaValue] = useState('')
  const [dadesError, setDadesError] = useState('')
  // edició RA/dif per pregunta: null = no editant, {} = editant
  const [editingRa, setEditingRa] = useState<Record<string, RaEdit | null>>({})
  const [savingRa, setSavingRa] = useState<Record<string, boolean>>({})
  // fitxers: local state per les preguntes
  const [questionFiles, setQuestionFiles] = useState<Record<string, QuestionFile[]>>({})
  const [uploadingFor, setUploadingFor] = useState<string | null>(null)
  const fileInputRefs = useRef<Record<string, HTMLInputElement | null>>({})

  useEffect(() => {
    if (examId) getExam(examId).then(e => {
      setExam(e)
      setPenalValue(e.penalitzacioChoice)
      // Inicialitzem fitxers des del DTO
      const fMap: Record<string, QuestionFile[]> = {}
      e.questions.forEach(q => { fMap[q.id] = q.files ?? [] })
      setQuestionFiles(fMap)
    })
  }, [examId])

  const savePenal = async () => {
    if (!examId) return
    const updated = await updateExamSettings(examId, { penalitzacioChoice: penalValue })
    setExam(updated); setPenalEdit(false)
  }

  const startEditDades = () => {
    if (!exam) return
    setTitolValue(exam.title); setDuradaValue(String(exam.durada)); setDadesError(''); setDadesEdit(true)
  }

  const saveDades = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!examId || !exam) return
    const durada = Number(duradaValue)
    if (!titolValue.trim()) { setDadesError('El títol no pot estar buit'); return }
    if (!Number.isInteger(durada) || durada < 1) { setDadesError('La durada ha de ser un nombre enter de minuts'); return }
    try {
      const updated = await updateExamSettings(examId, {
        title: titolValue.trim(),
        ...(durada !== exam.durada ? { durada } : {}),
      })
      setExam(updated); setDadesEdit(false)
    } catch (err: any) {
      setDadesError(err?.response?.data?.error || 'No s\'han pogut desar els canvis')
    }
  }

  const toggleUnaPregunta = async () => {
    if (!examId || !exam) return
    const updated = await updateExamSettings(examId, { unaPreguntaPerPantalla: !exam.unaPreguntaPerPantalla })
    setExam(updated)
  }

  const startEditRa = (q: Question) =>
    setEditingRa(prev => ({ ...prev, [q.id]: { ra: q.ra ?? '', dif: q.dificultat ?? '' } }))

  const cancelEditRa = (qId: string) =>
    setEditingRa(prev => ({ ...prev, [qId]: null }))

  /** Marca o desmarca "amb apunts" en una o més preguntes (p. ex. totes les d'una secció). */
  const setApunts = async (ids: string[], ambApunts: boolean) => {
    if (!examId) return
    for (const id of ids) await patchQuestion(examId, id, { ambApunts })
    setExam(prev => prev && {
      ...prev,
      questions: prev.questions.map(x => ids.includes(x.id) ? { ...x, ambApunts } : x)
    })
  }

  /** Preguntes d'una secció: les que hi ha fins a la secció següent. */
  const preguntesDeSeccio = (seccioId: string): Question[] => {
    if (!exam) return []
    const i = exam.questions.findIndex(x => x.id === seccioId)
    const out: Question[] = []
    for (const x of exam.questions.slice(i + 1)) {
      if (x.tipus === 'SECTION') break
      out.push(x)
    }
    return out
  }

  const toggleBarrejar = async (q: Question) => {
    if (!examId) return
    const barrejarOpcions = !q.barrejarOpcions
    await patchQuestion(examId, q.id, { barrejarOpcions })
    setExam(prev => prev && {
      ...prev,
      questions: prev.questions.map(x => x.id === q.id ? { ...x, barrejarOpcions } : x)
    })
  }

  const saveRa = async (qId: string) => {
    if (!examId) return
    const edit = editingRa[qId]
    if (!edit) return
    setSavingRa(prev => ({ ...prev, [qId]: true }))
    try {
      await patchQuestion(examId, qId, { ra: edit.ra, dificultat: edit.dif })
      setExam(prev => {
        if (!prev) return prev
        return {
          ...prev,
          questions: prev.questions.map(q =>
            q.id === qId ? { ...q, ra: edit.ra || undefined, dificultat: (edit.dif as Question['dificultat']) || undefined } : q
          )
        }
      })
      setEditingRa(prev => ({ ...prev, [qId]: null }))
    } finally {
      setSavingRa(prev => ({ ...prev, [qId]: false }))
    }
  }

  const handleDraft = (qId: string, val: string) =>
    setDraftAnswers(prev => ({ ...prev, [qId]: val }))

  const handleUpload = async (questionId: string, file: File) => {
    setUploadingFor(questionId)
    try {
      const qf = await uploadQuestionFile(questionId, file)
      setQuestionFiles(prev => ({ ...prev, [questionId]: [...(prev[questionId] ?? []), qf] }))
    } finally {
      setUploadingFor(null)
    }
  }

  const handleDelete = async (questionId: string, fileId: string) => {
    if (!confirm('Eliminar aquest fitxer?')) return
    await deleteQuestionFile(questionId, fileId)
    setQuestionFiles(prev => ({
      ...prev,
      [questionId]: (prev[questionId] ?? []).filter(f => f.id !== fileId)
    }))
  }

  if (!exam) return <Layout><p className="text-gray-400">Carregant…</p></Layout>

  return (
    <Layout>
      <div className="max-w-3xl space-y-6">
        {/* Avís de professor */}
        <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 flex items-center justify-between text-sm">
          <div className="flex items-center gap-3">
            <span className="text-amber-800 font-medium">Previsualització</span>
            <div className="flex rounded-lg overflow-hidden border border-amber-300 text-xs">
              <button onClick={() => setShowAnswers(false)}
                className={`px-3 py-1 transition-colors ${!showAnswers ? 'bg-amber-300 text-amber-900 font-semibold' : 'text-amber-700 hover:bg-amber-100'}`}>
                Vista alumne
              </button>
              <button onClick={() => setShowAnswers(true)}
                className={`px-3 py-1 transition-colors ${showAnswers ? 'bg-amber-300 text-amber-900 font-semibold' : 'text-amber-700 hover:bg-amber-100'}`}>
                Respostes
              </button>
            </div>
          </div>
          <Link to="/professor/exams" className="text-amber-700 hover:text-amber-900 underline text-xs">
            ← Tornar als examens
          </Link>
        </div>

        {/* Títol i durada */}
        <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 text-sm">
          {dadesEdit ? (
            <form onSubmit={saveDades} className="flex flex-wrap items-end gap-3">
              <label className="flex flex-col gap-1 flex-1 min-w-[12rem]">
                <span className="text-xs text-gray-500">Títol</span>
                <input value={titolValue} onChange={e => setTitolValue(e.target.value)} maxLength={255} autoFocus
                  className="border rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-brand-400" />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-xs text-gray-500">Durada (minuts)</span>
                <input type="number" min={1} max={480} value={duradaValue} onChange={e => setDuradaValue(e.target.value)}
                  disabled={exam.status !== 'DRAFT'}
                  title={exam.status !== 'DRAFT' ? 'Només es pot canviar en un esborrany' : undefined}
                  className="border rounded px-2 py-1 text-sm w-24 disabled:bg-gray-100 focus:outline-none focus:ring-1 focus:ring-brand-400" />
              </label>
              <button type="submit" className="bg-brand-600 text-white px-3 py-1 rounded text-xs hover:bg-brand-700">Guardar</button>
              <button type="button" onClick={() => setDadesEdit(false)}
                className="text-gray-400 text-xs hover:text-gray-600">Cancel·lar</button>
              {dadesError && <p className="w-full text-xs text-red-600">{dadesError}</p>}
            </form>
          ) : (
            <div className="flex flex-wrap items-center gap-4">
              <span className="text-gray-500">Títol:</span>
              <span className="font-medium text-gray-700">{exam.title}</span>
              <span className="text-gray-500">Durada:</span>
              <span className="font-medium text-gray-700">{exam.durada} min</span>
              <button onClick={startEditDades} className="text-xs text-brand-600 hover:underline">Editar</button>
            </div>
          )}
        </div>

        {/* Configuració penalització */}
        <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 flex items-center gap-4 text-sm">
          <span className="text-gray-500">Penalització respostes incorrectes (test):</span>
          {penalEdit ? (
            <>
              <select value={penalValue} onChange={e => setPenalValue(Number(e.target.value))}
                className="border rounded px-2 py-1 text-sm focus:outline-none focus:ring-1 focus:ring-brand-400">
                <option value={0}>Cap (0)</option>
                <option value={0.25}>1/4 del valor (25%)</option>
                <option value={0.3333}>1/3 del valor (33%)</option>
                <option value={0.5}>1/2 del valor (50%)</option>
                <option value={1}>Valor sencer (100%)</option>
              </select>
              <button onClick={savePenal}
                className="bg-brand-600 text-white px-3 py-1 rounded text-xs hover:bg-brand-700">Guardar</button>
              <button onClick={() => { setPenalEdit(false); setPenalValue(exam?.penalitzacioChoice ?? 0) }}
                className="text-gray-400 text-xs hover:text-gray-600">Cancel·lar</button>
            </>
          ) : (
            <>
              <span className="font-medium text-gray-700">
                {exam!.penalitzacioChoice > 0
                  ? `-${(exam!.penalitzacioChoice * 100).toFixed(0)}% del valor`
                  : 'Cap'}
              </span>
              <button onClick={() => setPenalEdit(true)} className="text-xs text-brand-600 hover:underline">Editar</button>
            </>
          )}
        </div>

        {/* Mode de presentació */}
        <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 flex items-center gap-4 text-sm">
          <label className="flex items-center gap-2 cursor-pointer">
            <input type="checkbox" checked={exam!.unaPreguntaPerPantalla} onChange={toggleUnaPregunta}
              className="accent-brand-600 w-4 h-4" />
            <span className="text-gray-700">Una pregunta per pantalla</span>
          </label>
          <span className="text-xs text-gray-400">
            L'alumne veu les preguntes d'una en una i hi navega lliurement. L'ordre de les preguntes i de les opcions de test ja és diferent per a cada alumne.
          </span>
        </div>

        {/* Capçalera de l'examen */}
        <div className="bg-brand-600 text-white rounded-xl p-6">
          <h1 className="text-xl font-bold">{exam.title}</h1>
          <p className="text-brand-100 text-sm mt-1">{exam.durada} min · {exam.questions.filter(q => q.tipus !== 'SECTION').length} preguntes</p>
          {exam.instruccions && <p className="text-white/80 text-sm mt-2 whitespace-pre-wrap">{exam.instruccions}</p>}
        </div>

        {/* Resum RA / dificultat */}
        {(() => {
          const real = exam.questions.filter(q => q.tipus !== 'SECTION')
          const raMap: Record<string, number> = {}
          const difMap: Record<string, number> = { baixa: 0, mitjana: 0, alta: 0, '—': 0 }
          real.forEach(q => {
            const ra = q.ra || '—'
            raMap[ra] = (raMap[ra] ?? 0) + 1
            const dif = q.dificultat || '—'
            difMap[dif] = (difMap[dif] ?? 0) + 1
          })
          const hasRa  = real.some(q => q.ra)
          const hasDif = real.some(q => q.dificultat)
          if (!hasRa && !hasDif) return null
          return (
            <div className="bg-white border border-gray-200 rounded-xl px-4 py-3 text-sm space-y-2">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide">Distribució de preguntes</p>
              {hasRa && (
                <div className="flex flex-wrap gap-2">
                  <span className="text-xs text-gray-400 self-center">RA:</span>
                  {Object.entries(raMap).sort(([a],[b]) => a.localeCompare(b)).map(([ra, count]) => (
                    <span key={ra} className="bg-indigo-100 text-indigo-700 text-xs px-2 py-0.5 rounded font-medium">
                      {ra} · {count}
                    </span>
                  ))}
                </div>
              )}
              {hasDif && (
                <div className="flex flex-wrap gap-2">
                  <span className="text-xs text-gray-400 self-center">Dificultat:</span>
                  {(['baixa','mitjana','alta','—'] as const).filter(d => difMap[d] > 0).map(d => (
                    <span key={d} className={`text-xs px-2 py-0.5 rounded font-medium ${d === '—' ? 'bg-gray-100 text-gray-500' : DIF_COLORS[d]}`}>
                      {d} · {difMap[d]}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )
        })()}

        {/* Preguntes */}
        {exam.questions.map(q => {
          if (q.tipus === 'SECTION') {
            return (
              <div key={q.id} className="pt-2 pb-1 space-y-1">
                <div className="flex items-center gap-3">
                  <div className="flex-1 h-px bg-brand-200" />
                  <span className="text-brand-700 font-semibold text-sm px-2">{q.enunciat}</span>
                  <div className="flex-1 h-px bg-brand-200" />
                </div>
                {(() => {
                  const preguntes = preguntesDeSeccio(q.id)
                  if (preguntes.length === 0) return null
                  const totes = preguntes.every(x => x.ambApunts)
                  return (
                    <label className="flex items-center justify-center gap-2 text-xs text-gray-500 cursor-pointer"
                      title="Els alumnes poden fer servir apunts en paper en aquestes preguntes. L'informe de còpies hi aplica un llindar més alt.">
                      <input type="checkbox" checked={totes} onChange={() => setApunts(preguntes.map(x => x.id), !totes)}
                        className="accent-brand-600 w-3.5 h-3.5" />
                      📖 Amb apunts (tota la secció)
                    </label>
                  )
                })()}
              </div>
            )
          }

          const isScript = SCRIPT_TYPES.includes(q.tipus)
          const isML     = MULTILINE_TYPES.includes(q.tipus)
          const isHtml   = q.tipus === 'HTML_CSS'
          const files    = questionFiles[q.id] ?? []
          const editing  = editingRa[q.id]

          return (
            <div key={q.id} className="bg-white border rounded-xl p-6 space-y-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded font-medium">{q.ordre}</span>
                  <span className="text-xs text-gray-400">{TYPE_LABEL[q.tipus] ?? q.tipus} · {q.punts} pts</span>
                  <label className="flex items-center gap-1 text-xs text-gray-500 cursor-pointer"
                    title="Els alumnes poden fer servir apunts en paper en aquesta pregunta">
                    <input type="checkbox" checked={q.ambApunts} onChange={() => setApunts([q.id], !q.ambApunts)}
                      className="accent-brand-600 w-3.5 h-3.5" />
                    📖 Amb apunts
                  </label>
                  {/* RA i dificultat (badges o edició inline) */}
                  {editing ? (
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <input
                        type="text"
                        value={editing.ra}
                        onChange={e => setEditingRa(prev => ({ ...prev, [q.id]: { ...prev[q.id]!, ra: e.target.value } }))}
                        placeholder="RA (p.ex. RA1)"
                        className="border rounded px-2 py-0.5 text-xs w-24 focus:outline-none focus:ring-1 focus:ring-brand-400"
                      />
                      <select
                        value={editing.dif}
                        onChange={e => setEditingRa(prev => ({ ...prev, [q.id]: { ...prev[q.id]!, dif: e.target.value } }))}
                        className="border rounded px-1.5 py-0.5 text-xs focus:outline-none focus:ring-1 focus:ring-brand-400">
                        <option value="">— dif —</option>
                        <option value="baixa">baixa</option>
                        <option value="mitjana">mitjana</option>
                        <option value="alta">alta</option>
                      </select>
                      <button onClick={() => saveRa(q.id)} disabled={savingRa[q.id]}
                        className="text-xs bg-brand-600 text-white px-2 py-0.5 rounded hover:bg-brand-700 disabled:opacity-50">
                        {savingRa[q.id] ? '…' : 'OK'}
                      </button>
                      <button onClick={() => cancelEditRa(q.id)}
                        className="text-xs text-gray-400 hover:text-gray-600">×</button>
                    </div>
                  ) : (
                    <button onClick={() => startEditRa(q)} title="Editar RA i dificultat"
                      className="flex items-center gap-1.5 group">
                      {q.ra
                        ? <span className="bg-indigo-100 text-indigo-700 text-xs px-2 py-0.5 rounded font-medium">{q.ra}</span>
                        : <span className="text-xs text-gray-300 group-hover:text-gray-400">+ RA</span>}
                      {q.dificultat
                        ? <span className={`text-xs px-2 py-0.5 rounded font-medium ${DIF_COLORS[q.dificultat]}`}>{q.dificultat}</span>
                        : q.ra && <span className="text-xs text-gray-300 group-hover:text-gray-400">+ dif</span>}
                    </button>
                  )}
                </div>
              </div>

              <Md className="text-gray-800">{q.enunciat}</Md>

              {/* Gestió de fitxers de dades */}
              <div className="border border-dashed border-gray-300 rounded-lg p-3 space-y-2 bg-gray-50">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium text-gray-600">Arxius de dades adjunts (disponibles a <code>/data/files/</code>)</span>
                  <div>
                    <input
                      ref={el => { fileInputRefs.current[q.id] = el }}
                      type="file"
                      className="hidden"
                      onChange={async e => {
                        const f = e.target.files?.[0]
                        if (f) await handleUpload(q.id, f)
                        if (fileInputRefs.current[q.id]) fileInputRefs.current[q.id]!.value = ''
                      }}
                    />
                    <button
                      onClick={() => fileInputRefs.current[q.id]?.click()}
                      disabled={uploadingFor === q.id}
                      className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700 disabled:opacity-50">
                      {uploadingFor === q.id ? 'Pujant…' : '+ Afegir fitxer'}
                    </button>
                  </div>
                </div>
                {files.length === 0 ? (
                  <p className="text-xs text-gray-400 italic">Cap arxiu adjunt</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {files.map(f => (
                      <div key={f.id} className="flex items-center gap-1 bg-white border rounded px-2 py-1 text-xs">
                        <button type="button" onClick={() => downloadQuestionFile(f.id, f.filename)}
                          className="text-blue-600 hover:underline">📎 {f.filename}</button>
                        <span className="text-gray-400">({(f.fileSize / 1024).toFixed(1)} KB)</span>
                        <button onClick={() => handleDelete(q.id, f.id)}
                          className="text-red-400 hover:text-red-600 ml-1 leading-none">×</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Vista de l'àrea de resposta */}
              {q.tipus === 'CHOICE' && q.choices && (
                <div className="space-y-2 mt-1">
                  <label className="flex items-center gap-2 text-xs text-gray-500 cursor-pointer w-fit"
                    title="Desactiva-ho si alguna opció depèn de la posició, com ara «Totes les anteriors»">
                    <input type="checkbox" checked={q.barrejarOpcions} onChange={() => toggleBarrejar(q)}
                      className="accent-brand-600 w-3.5 h-3.5" />
                    Barrejar l'ordre de les opcions per a cada alumne
                  </label>
                  {q.choices.map((choice) => {
                    const letter    = choice.charAt(0)
                    const isCorrect = showAnswers && q.correctChoice === letter
                    const isSelected = !showAnswers && draftAnswers[q.id] === letter
                    return (
                      <label key={letter}
                        className={`flex items-center gap-3 px-4 py-2.5 rounded-lg border text-sm cursor-pointer transition-colors ${
                          isCorrect
                            ? 'border-green-400 bg-green-50 font-medium text-green-800'
                            : isSelected ? 'border-brand-400 bg-brand-50' : 'border-gray-200 text-gray-700 hover:bg-gray-50'
                        }`}>
                        {showAnswers ? (
                          <span className={`w-5 h-5 rounded-full border-2 flex items-center justify-center text-xs flex-shrink-0 ${
                            isCorrect ? 'border-green-500 bg-green-500 text-white' : 'border-gray-300'
                          }`}>{isCorrect ? '✓' : ''}</span>
                        ) : (
                          <input type="radio" name={`preview-${q.id}`} value={letter}
                            checked={isSelected}
                            onChange={() => handleDraft(q.id, letter)}
                            className="accent-brand-600 w-4 h-4 flex-shrink-0" />
                        )}
                        {choice}
                      </label>
                    )
                  })}
                </div>
              )}

              {(isScript || isHtml) ? (
                <textarea
                  rows={isML || isHtml ? 6 : 2}
                  value={draftAnswers[q.id] ?? ''}
                  onChange={e => !showAnswers && handleDraft(q.id, e.target.value)}
                  readOnly={showAnswers}
                  placeholder={isHtml ? '<html>…' : isML ? '# Script…' : '$ comanda…'}
                  className="w-full border rounded bg-gray-900 text-green-300 text-xs px-4 py-2 font-mono resize-y focus:outline-none focus:ring-1 focus:ring-brand-400"
                />
              ) : q.tipus !== 'CHOICE' ? (
                <textarea
                  rows={q.tipus === 'SHORT' ? 3 : 6}
                  value={draftAnswers[q.id] ?? ''}
                  onChange={e => !showAnswers && handleDraft(q.id, e.target.value)}
                  readOnly={showAnswers}
                  placeholder="Escriu la teva resposta aquí..."
                  className="w-full border rounded-lg px-3 py-2 text-sm resize-y focus:outline-none focus:ring-2 focus:ring-brand-400"
                />
              ) : null}

              {/* Resposta model */}
              {showAnswers && q.modelResposta && (
                <details className="mt-2">
                  <summary className="text-xs text-brand-600 cursor-pointer hover:underline select-none">
                    Veure resposta model (professor)
                  </summary>
                  <pre className="mt-2 bg-brand-50 border border-brand-100 rounded p-3 text-xs text-brand-900 whitespace-pre-wrap">
                    {q.modelResposta}
                  </pre>
                </details>
              )}
            </div>
          )
        })}

        <div className="pb-8 text-center text-xs text-gray-400">— Fi de la previsualització —</div>
      </div>
    </Layout>
  )
}

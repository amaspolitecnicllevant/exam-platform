import { useState } from 'react'
import type { ReactNode, KeyboardEvent } from 'react'
import type { Answer, Question, Session } from '../../types'
import { descarregaFitxerResposta } from '../../api/respostaFitxer'
import { formatMida } from '../../utils/fitxers'

export const EXEC_TYPES: Question['tipus'][] = ['BASH_CMD', 'PS_CMD', 'BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG']
export const TEXT_TYPES: Question['tipus'][] = ['TEXT', 'SHORT', 'LONG']

export const fmtPts = (n: number) => Number(n.toFixed(2)).toString().replace('.', ',')

/** Resposta que el professor ha de revisar: no és de test, no està anul·lada i no té nota manual. */
export function isPendent(session: Session, q: Question | undefined, a: Answer | undefined): boolean {
  return !!q && !!a && session.status === 'SUBMITTED'
    && q.tipus !== 'CHOICE' && q.tipus !== 'SECTION' && !q.anulada && a.manualScore == null
}

/** Passa el focus a la casella de nota següent de la pàgina (correcció amb el teclat). */
function focusSeguent(actual: HTMLInputElement) {
  const caselles = Array.from(document.querySelectorAll<HTMLInputElement>('input[data-nota]'))
  const seguent = caselles[caselles.indexOf(actual) + 1]
  if (seguent) {
    seguent.focus()
    seguent.select()
    seguent.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }
}

export interface AccionsCorreccio {
  onScore: (session: Session, answer: Answer, score: number) => void
  onAccept: (session: Session, answer: Answer) => void
  onRun: (session: Session, answer: Answer) => void
  onComment: (session: Session, answer: Answer, comentari: string) => void
}

interface Props extends AccionsCorreccio {
  q: Question
  answer?: Answer
  session: Session
  /** Capçalera: l'enunciat (vista per alumne) o el nom de l'alumne (vista per pregunta) */
  titol: ReactNode
  /** Mostrar la resposta correcta i els conceptes clau (a la vista per pregunta ja surten a dalt) */
  mostrarReferencia?: boolean
}

/** Una resposta a corregir: contingut, proposta, nota, comentari i execució. */
export default function RespostaCorreccio({ q, answer, session, titol, mostrarReferencia = true,
                                            onScore, onAccept, onRun, onComment }: Props) {
  const [htmlPreview, setHtmlPreview] = useState(false)
  const isChoice  = q.tipus === 'CHOICE'
  const isCorrect = isChoice && answer?.contingut?.trim().toLowerCase() === q.correctChoice?.trim().toLowerCase()
  const pendent   = isPendent(session, q, answer)
  const motius    = answer?.autoFeedback?.split('\n').filter(Boolean) ?? []

  const desaNota = (input: HTMLInputElement) => {
    if (!answer) return
    if (input.value === '') return
    const v = parseFloat(input.value.replace(',', '.'))
    if (!Number.isNaN(v) && v !== answer.manualScore) onScore(session, answer, v)
  }

  // Intro: desa i passa a la següent. Casella buida + Intro: accepta la proposta.
  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key !== 'Enter' || !answer) return
    e.preventDefault()
    const input = e.currentTarget
    if (input.value === '' && pendent && answer.autoScore != null) {
      onAccept(session, answer)
    } else {
      desaNota(input)
    }
    input.dataset.desat = '1'   // evita desar-la un altre cop en perdre el focus
    focusSeguent(input)
  }

  return (
    <div className={`bg-white border rounded-xl p-4 space-y-2 ${q.anulada ? 'border-green-200' : ''} ${pendent ? 'border-amber-300' : ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2 min-w-0">{titol}</div>
        <div className="flex items-center gap-2 shrink-0">
          {q.anulada && (
            <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded font-semibold">
              BONUS · {fmtPts(q.punts)} pts
            </span>
          )}
          {isChoice && !q.anulada && (
            <span className={`text-xs px-2 py-0.5 rounded font-semibold ${isCorrect ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
              {isCorrect ? '✓' : '✗'} {fmtPts(answer?.autoScore ?? 0)} pts
            </span>
          )}
          {!isChoice && answer && !q.anulada && session.status === 'SUBMITTED' && (
            pendent
              ? <span className="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded">Pendent de revisar</span>
              : <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">✓ Revisada</span>
          )}
          {!isChoice && answer && !q.anulada && (
            <label className="flex items-center gap-1">
              <span className="text-xs text-gray-400">pts:</span>
              <input type="text" inputMode="decimal" data-nota
                aria-label={`Nota (sobre ${q.punts})`}
                key={`${answer.id}-${answer.manualScore ?? ''}`}
                defaultValue={answer.manualScore != null ? fmtPts(answer.manualScore) : ''}
                placeholder={answer.autoScore != null ? fmtPts(answer.autoScore) : ''}
                onKeyDown={onKeyDown}
                onFocus={e => { delete e.currentTarget.dataset.desat }}
                onBlur={e => { if (!e.currentTarget.dataset.desat) desaNota(e.currentTarget) }}
                className="w-16 border rounded px-2 py-0.5 text-sm text-center focus:outline-none focus:ring-2 focus:ring-brand-400" />
              <span className="text-xs text-gray-400">/ {fmtPts(q.punts)}</span>
            </label>
          )}
        </div>
      </div>

      {isChoice ? (
        <div className="text-sm">
          {answer?.contingut
            ? <span className={`font-mono font-semibold ${isCorrect ? 'text-green-700' : 'text-red-700'}`}>
                {answer.contingut.toUpperCase()}) {q.choices?.find(c => c.startsWith(answer.contingut ?? ''))?.replace(/^[a-zA-Z]\)\s*/, '')}
              </span>
            : <span className="text-gray-400 italic text-xs">sense resposta</span>
          }
          {!isCorrect && q.correctChoice && (
            <span className="ml-3 text-xs text-green-600">
              Correcta: {q.correctChoice.toUpperCase()}) {q.choices?.find(c => c.startsWith(q.correctChoice ?? ''))?.replace(/^[a-zA-Z]\)\s*/, '')}
            </span>
          )}
        </div>
      ) : q.tipus === 'FILE_UPLOAD' ? (
        <div className="bg-gray-50 rounded-lg p-3 text-sm flex flex-wrap items-center gap-3">
          {answer?.fitxerNom ? (
            <>
              <span aria-hidden>📎</span>
              <span className="font-medium text-gray-800 break-all">{answer.fitxerNom}</span>
              <span className="text-xs text-gray-400">{formatMida(answer.fitxerMida ?? 0)}</span>
              <button type="button"
                onClick={() => descarregaFitxerResposta(session.id, q.id, answer.fitxerNom ?? 'lliurament')}
                className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700 ml-auto">
                Descarregar
              </button>
            </>
          ) : <span className="text-gray-400 italic">no ha pujat cap fitxer</span>}
        </div>
      ) : (
        <div className="bg-gray-50 rounded-lg p-3 text-sm whitespace-pre-wrap font-mono text-gray-700 max-h-48 overflow-auto">
          {answer?.contingut || <span className="text-gray-400 italic">sense resposta</span>}
        </div>
      )}

      {/* Resposta correcta i conceptes clau */}
      {mostrarReferencia && !isChoice && (q.modelResposta || q.claus) && <Referencia q={q} />}

      {/* Nota proposada i motius */}
      {!isChoice && !q.anulada && answer?.autoScore != null && (
        <div className="border border-amber-200 bg-amber-50 rounded-lg p-3 text-sm space-y-1">
          <div className="flex items-center justify-between gap-3">
            <span className="text-amber-900">
              Proposta: <strong>{fmtPts(answer.autoScore)}</strong> / {fmtPts(q.punts)}
              {answer.manualScore != null && answer.manualScore !== answer.autoScore && (
                <span className="text-xs text-gray-500 ml-2">(nota revisada: {fmtPts(answer.manualScore)})</span>
              )}
            </span>
            {pendent && (
              <button onClick={() => onAccept(session, answer)}
                className="text-xs bg-amber-600 text-white px-3 py-1 rounded hover:bg-amber-700">
                Acceptar
              </button>
            )}
          </div>
          {motius.length > 0 && (
            <ul className="text-xs space-y-0.5">
              {motius.map((m, i) => (
                <li key={i} className={m.startsWith('✓') ? 'text-green-700' : m.startsWith('−') ? 'text-red-700' : 'text-gray-600'}>
                  {m}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {answer?.revisioIa && (
        <div className="text-xs bg-violet-50 border border-violet-200 rounded-lg px-3 py-2 text-violet-900">
          <strong>Revisada amb IA</strong>
          {' · '}{answer.revisioIa.notaAbans != null ? `${fmtPts(answer.revisioIa.notaAbans)} → ` : 'sense nota → '}
          <strong>{answer.manualScore != null ? fmtPts(answer.manualScore) : '—'}</strong>
          {answer.revisioIa.justificacio && <span className="block text-violet-800/90 mt-0.5">{answer.revisioIa.justificacio}</span>}
        </div>
      )}
      {!isChoice && !q.anulada && answer?.autoScore == null && answer?.contingut?.trim()
        && EXEC_TYPES.includes(q.tipus) && session.status === 'SUBMITTED' && (
        <p className="text-xs text-gray-500 italic">
          Correcció automàtica en curs… Torna a carregar d'aquí a uns segons o prem ▶ Executar.
        </p>
      )}

      {/* Previsualització HTML/CSS */}
      {q.tipus === 'HTML_CSS' && answer?.contingut && (
        <div className="space-y-1">
          <button onClick={() => setHtmlPreview(v => !v)} className="text-xs text-brand-600 hover:underline">
            {htmlPreview ? 'Amagar' : 'Mostrar'} previsualització HTML
          </button>
          {htmlPreview && (
            <iframe srcDoc={answer.contingut} sandbox="allow-scripts"
              className="w-full border rounded bg-white" style={{ height: '250px' }}
              title={`html-preview-${answer.id}`} />
          )}
        </div>
      )}

      {answer?.executionOutput && (
        <pre className="bg-gray-900 text-green-300 rounded p-2 text-xs overflow-auto max-h-32">
          {answer.executionOutput}
        </pre>
      )}

      <div className="flex items-start gap-3">
        {EXEC_TYPES.includes(q.tipus) && answer && (
          <button onClick={() => onRun(session, answer)}
            className="text-xs bg-gray-800 text-white px-3 py-1 rounded hover:bg-gray-700 shrink-0">
            ▶ Executar
          </button>
        )}
        {answer && session.status === 'SUBMITTED' && (
          <textarea
            key={`${answer.id}-c-${answer.comentari ?? ''}`}
            defaultValue={answer.comentari ?? ''}
            rows={answer.comentari ? 2 : 1}
            maxLength={2000}
            aria-label="Comentari per a l'alumne"
            placeholder="Comentari per a l'alumne (el veurà quan publiquis les notes)"
            onBlur={e => {
              const nou = e.currentTarget.value.trim()
              if (nou !== (answer.comentari ?? '')) onComment(session, answer, nou)
            }}
            className="flex-1 border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-700 resize-y focus:outline-none focus:ring-1 focus:ring-brand-400" />
        )}
      </div>
    </div>
  )
}

/** Resposta correcta i conceptes clau d'una pregunta. */
export function Referencia({ q, oberta }: { q: Question; oberta?: boolean }) {
  return (
    <details className="text-sm" open={oberta ?? TEXT_TYPES.includes(q.tipus)}>
      <summary className="text-xs text-green-700 cursor-pointer select-none">
        {TEXT_TYPES.includes(q.tipus) ? 'Resposta correcta' : 'Solució de referència'}
      </summary>
      <div className="mt-1 bg-green-50 border border-green-200 rounded-lg p-3 space-y-2">
        {q.modelResposta && (
          <div className={`whitespace-pre-wrap text-gray-700 ${EXEC_TYPES.includes(q.tipus) ? 'font-mono text-xs' : ''}`}>
            {q.modelResposta}
          </div>
        )}
        {q.claus && (
          <div className="flex flex-wrap gap-1 items-center">
            <span className="text-xs text-gray-500">Conceptes clau:</span>
            {q.claus.split('\n').filter(l => l.trim()).map(l => (
              <span key={l} className="text-xs bg-white border border-green-300 text-green-800 px-1.5 py-0.5 rounded">
                {l.trim()}
              </span>
            ))}
          </div>
        )}
      </div>
    </details>
  )
}

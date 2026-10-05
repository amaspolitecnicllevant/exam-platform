import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getMonitor, resetSession, type MonitorEntry } from '../../api/sessions'
import { getExam, reopenWindow } from '../../api/exams'
import type { Exam } from '../../types'
import { useLlindarFocus } from '../../context/ConfiguracioContext'

const REFRESH_MS = 15_000

function elapsed(iso?: string | null): string {
  if (!iso) return 'No començat'
  const secs = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (secs < 60) return `${secs}s`
  if (secs < 3600) return `${Math.floor(secs / 60)}m ${secs % 60}s`
  return `${Math.floor(secs / 3600)}h ${Math.floor((secs % 3600) / 60)}m`
}

export default function MonitorPage() {
  const llindarFocus = useLlindarFocus()
  const { examId } = useParams<{ examId: string }>()
  const [exam, setExam]         = useState<Exam | null>(null)
  const [entries, setEntries]   = useState<MonitorEntry[]>([])
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date())
  const [, setTick]             = useState(0)
  const [reopening, setReopening] = useState(false)
  const intervalRef             = useRef<ReturnType<typeof setInterval> | null>(null)

  const refresh = () => {
    if (!examId) return
    getMonitor(examId).then(data => {
      setEntries(data)
      setLastRefresh(new Date())
    })
  }

  useEffect(() => {
    if (!examId) return
    getExam(examId).then(setExam)
    refresh()
    intervalRef.current = setInterval(refresh, REFRESH_MS)
    // Tick per actualitzar el temps transcorregut sense fer peticions
    const tickInterval = setInterval(() => setTick(t => t + 1), 1000)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
      clearInterval(tickInterval)
    }
  }, [examId])

  const inProgress  = entries.filter(e => e.status === 'IN_PROGRESS')
  const submitted   = entries.filter(e => e.status === 'SUBMITTED')

  return (
    <Layout>
      <div className="space-y-4">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-brand-700">
              Monitor en temps real
            </h1>
            {exam && (
              <p className="text-sm text-gray-500 mt-0.5">
                {exam.title} · {exam.durada} min
              </p>
            )}
          </div>
          <div className="text-right">
            <div className="flex items-center gap-2">
              <span className="inline-block w-2 h-2 rounded-full bg-green-400 animate-pulse" />
              <span className="text-xs text-gray-500">
                Actualitza cada {REFRESH_MS / 1000}s
              </span>
            </div>
            <p className="text-xs text-gray-400 mt-0.5">
              Última actualització: {lastRefresh.toLocaleTimeString('ca-ES')}
            </p>
            <button onClick={refresh}
              className="mt-1 text-xs text-brand-600 hover:underline">
              Actualitzar ara
            </button>
          </div>
        </div>

        {exam?.status === 'PUBLISHED' && exam.scheduledAt && (
          <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-amber-800">Finestra d'accés</p>
              <p className="text-xs text-amber-600 mt-0.5">
                Si un alumne arriba tard i no pot entrar, obre una nova finestra de 20 minuts.
              </p>
            </div>
            <button
              onClick={async () => {
                if (!examId || !confirm('Reobrir la finestra d\'accés? Els alumnes podran entrar durant els propers 20 minuts.')) return
                setReopening(true)
                try {
                  await reopenWindow(examId)
                  await getExam(examId).then(setExam)
                } finally {
                  setReopening(false)
                }
              }}
              disabled={reopening}
              className="shrink-0 ml-4 bg-amber-600 text-white text-sm px-4 py-2 rounded-lg hover:bg-amber-700 disabled:opacity-50">
              {reopening ? 'Reobrint…' : 'Reobrir accés'}
            </button>
          </div>
        )}

        {/* Avís de pèrdues de focus */}
        {(() => {
          const sospitosos = entries.filter(e => e.focusLossCount >= llindarFocus)
          if (sospitosos.length === 0) return null
          return (
            <div role="alert" className="bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-sm text-red-800">
              <p className="font-semibold">
                ⚠ {sospitosos.length === 1 ? '1 alumne ha' : `${sospitosos.length} alumnes han`} arribat al llindar de {llindarFocus} pèrdues de focus
              </p>
              <p className="mt-1">
                {sospitosos.map(e => `${e.studentName} (${e.focusLossCount})`).join(', ')}
              </p>
              <p className="text-xs text-red-600 mt-1">
                Una pèrdua de focus vol dir que l'alumne ha canviat de finestra o de pestanya. No és necessàriament una còpia.
              </p>
            </div>
          )
        })()}

        {/* Resum */}
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-white border rounded-xl p-4 text-center">
            <p className="text-3xl font-bold text-brand-600">{entries.length}</p>
            <p className="text-xs text-gray-500 mt-1">Alumnes totals</p>
          </div>
          <div className="bg-white border rounded-xl p-4 text-center">
            <p className="text-3xl font-bold text-green-600">{inProgress.length}</p>
            <p className="text-xs text-gray-500 mt-1">En curs</p>
          </div>
          <div className="bg-white border rounded-xl p-4 text-center">
            <p className="text-3xl font-bold text-gray-500">{submitted.length}</p>
            <p className="text-xs text-gray-500 mt-1">Entregats</p>
          </div>
        </div>

        {/* Alumnes EN CURS */}
        {inProgress.length > 0 && (
          <section>
            <h2 className="text-sm font-semibold text-green-700 uppercase tracking-wide mb-2 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse inline-block" />
              En curs ({inProgress.length})
            </h2>
            <div className="bg-white border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-xs font-medium text-gray-500 uppercase">
                  <tr>
                    <th className="px-4 py-3 text-left">Alumne</th>
                    <th className="px-4 py-3 text-left">IP</th>
                    <th className="px-4 py-3 text-center">Respostes</th>
                    <th className="px-4 py-3 text-center">Pèrdues focus</th>
                    <th className="px-4 py-3 text-right">Temps actiu</th>
                  </tr>
                </thead>
                <tbody>
                  {inProgress.map(e => (
                    <tr key={e.sessionId} className={`border-t ${e.focusLossCount >= llindarFocus ? 'bg-red-50 hover:bg-red-100' : 'hover:bg-green-50'}`}>
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-800">{e.studentName}</p>
                        <p className="text-xs text-gray-400">{e.studentEmail}</p>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">
                        {e.clientIp ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className="bg-brand-100 text-brand-700 px-2 py-0.5 rounded text-xs font-medium">
                          {e.answersCount} / {exam?.questions.filter(q => q.tipus !== 'SECTION').length ?? '?'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        {e.focusLossCount > 0 ? (
                          <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                            e.focusLossCount >= llindarFocus ? 'bg-red-100 text-red-700' : 'bg-yellow-100 text-yellow-700'
                          }`}>
                            ⚠ {e.focusLossCount}
                          </span>
                        ) : (
                          <span className="text-gray-300 text-xs">0</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-xs text-gray-500">
                        {elapsed(e.startedAt)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {/* Alumnes ENTREGATS */}
        {submitted.length > 0 && (
          <section>
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-2">
              Entregats ({submitted.length})
            </h2>
            <div className="bg-white border rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-xs font-medium text-gray-500 uppercase">
                  <tr>
                    <th className="px-4 py-3 text-left">Alumne</th>
                    <th className="px-4 py-3 text-left">IP</th>
                    <th className="px-4 py-3 text-center">Pèrdues focus</th>
                    <th className="px-4 py-3 text-right">Entregat fa</th>
                    <th className="px-4 py-3"></th>
                  </tr>
                </thead>
                <tbody>
                  {submitted.map(e => (
                    <tr key={e.sessionId} className="border-t hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <p className="font-medium text-gray-700">{e.studentName}</p>
                        <p className="text-xs text-gray-400">{e.studentEmail}</p>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-500">
                        {e.clientIp ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-center">
                        {e.focusLossCount > 0 ? (
                          <span className={`px-2 py-0.5 rounded text-xs ${e.focusLossCount >= llindarFocus ? 'bg-red-100 text-red-700 font-semibold' : 'bg-yellow-100 text-yellow-700'}`}>
                            {e.focusLossCount >= llindarFocus && '⚠ '}{e.focusLossCount}
                          </span>
                        ) : (
                          <span className="text-gray-300 text-xs">0</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-xs text-gray-400">
                        {e.submittedAt ? elapsed(e.submittedAt) : '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={async () => {
                            if (!confirm(`Reiniciar l'examen de ${e.studentName}? Les respostes es conserven però podrà tornar a enviar.`)) return
                            await resetSession(e.sessionId)
                            refresh()
                          }}
                          className="text-xs border border-orange-300 text-orange-600 px-2 py-1 rounded hover:bg-orange-50">
                          Reiniciar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}

        {entries.length === 0 && (
          <div className="bg-white border rounded-xl p-12 text-center">
            <p className="text-gray-400">Cap alumne ha iniciat l'examen encara.</p>
            <p className="text-xs text-gray-300 mt-1">S'actualitza automàticament cada {REFRESH_MS / 1000}s</p>
          </div>
        )}
      </div>
    </Layout>
  )
}

import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getPublished } from '../../api/exams'
import { getMySubmitted } from '../../api/sessions'
import type { Exam, Session } from '../../types'

function groupBy<T>(items: T[], key: (item: T) => string): [string, T[]][] {
  const map = new Map<string, T[]>()
  for (const item of items) {
    const k = key(item)
    const arr = map.get(k) ?? []
    arr.push(item)
    map.set(k, arr)
  }
  return Array.from(map.entries())
}

export default function ExamListPage() {
  const [exams, setExams]   = useState<Exam[] | null>(null)
  const [past, setPast]     = useState<Session[]>([])
  const [dots, setDots]     = useState('.')
  const navigate            = useNavigate()

  const check = async () => {
    const list = await getPublished()
    if (list.length === 1 && past.length === 0) {
      navigate(`/student/exams/${list[0].id}`, { replace: true })
      return
    }
    setExams(list)
  }

  useEffect(() => {
    check()
    getMySubmitted().then(setPast)
  }, [])

  useEffect(() => {
    if (exams !== null && exams.length > 0) return
    const id = setInterval(check, 15000)
    return () => clearInterval(id)
  }, [exams])

  useEffect(() => {
    if (exams !== null && exams.length > 0) return
    const id = setInterval(() => setDots(d => d.length >= 3 ? '.' : d + '.'), 600)
    return () => clearInterval(id)
  }, [exams])

  if (exams === null) {
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-gray-400 gap-4">
          <div className="w-8 h-8 border-4 border-brand-300 border-t-brand-600 rounded-full animate-spin" />
          <p className="text-sm">Carregant{dots}</p>
        </div>
      </Layout>
    )
  }

  const hasActive = exams.length > 0
  const hasPast   = past.length > 0

  if (!hasActive && !hasPast) {
    return (
      <Layout examMode>
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-6 text-center">
          <div className="w-16 h-16 border-4 border-brand-200 border-t-brand-600 rounded-full animate-spin" />
          <div>
            <p className="text-xl font-semibold text-brand-700">Esperant l'inici de l'examen{dots}</p>
            <p className="text-sm text-gray-400 mt-2">La pàgina s'actualitzarà automàticament quan el professor activi l'examen.</p>
          </div>
        </div>
      </Layout>
    )
  }

  const activeGroups = groupBy(exams, e => e.modulNom ?? 'Sense assignatura')
  const pastGroups   = groupBy(past,  s => s.modulNom  ?? 'Sense assignatura')

  return (
    <Layout examMode>
      <div className="max-w-lg mx-auto mt-8 space-y-8">

        {/* Examens actius, agrupats per assignatura */}
        {hasActive && (
          <section className="space-y-5">
            <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Examen en curs</h2>
            {activeGroups.map(([modul, items]) => (
              <div key={modul} className="space-y-2">
                <p className="text-xs font-semibold text-brand-600 px-1">{modul}</p>
                {items.map(exam => (
                  <button key={exam.id}
                    onClick={() => navigate(`/student/exams/${exam.id}`)}
                    className="w-full text-left bg-white border-2 border-brand-400 rounded-xl px-6 py-4 hover:bg-brand-50 transition-colors">
                    <p className="font-medium text-gray-900">{exam.title}</p>
                    <p className="text-sm text-gray-400 mt-1">{exam.durada} min</p>
                  </button>
                ))}
              </div>
            ))}
          </section>
        )}

        {/* Examens passats, agrupats per assignatura */}
        {hasPast && (
          <section className="space-y-5">
            <div className="flex items-baseline justify-between gap-2">
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">Examens anteriors</h2>
              <Link to="/student/historial" className="text-xs text-brand-600 hover:underline">Veure l'historial amb notes →</Link>
            </div>
            {pastGroups.map(([modul, items]) => (
              <div key={modul} className="space-y-2">
                <p className="text-xs font-semibold text-brand-600 px-1">{modul}</p>
                {items.map(s => (
                  <Link key={s.id}
                    to={`/student/sessions/${s.id}/results`}
                    className="block bg-white border border-gray-200 rounded-xl px-6 py-4 hover:border-gray-300 hover:bg-gray-50 transition-colors">
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-medium text-gray-800">{s.examTitle}</p>
                      {s.notesVisibles ? (
                        <span className="text-xs bg-teal-100 text-teal-700 px-2 py-0.5 rounded-full shrink-0">Notes disponibles</span>
                      ) : (
                        <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full shrink-0">Pendent de correcció</span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400 mt-1">
                      {s.submittedAt
                        ? new Date(s.submittedAt).toLocaleString('ca-ES', { dateStyle: 'medium', timeStyle: 'short' })
                        : s.startedAt
                          ? new Date(s.startedAt).toLocaleDateString('ca-ES', { dateStyle: 'medium' })
                          : '—'
                      }
                    </p>
                  </Link>
                ))}
              </div>
            ))}
          </section>
        )}

      </div>
    </Layout>
  )
}

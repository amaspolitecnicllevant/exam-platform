import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getExam } from '../../api/exams'
import { getSessionsByExam, deleteSession } from '../../api/sessions'
import type { Exam, Session } from '../../types'
import { taulaNotes, ordenaFiles, csvTaulaNotes, NOTA_APROVAT, type ColumnaOrdre } from '../../utils/taulaNotes'
import { nomExportacio } from '../../utils/fitxers'

const fmt = (n: number | null) => n == null ? '—' : n.toFixed(2).replace('.', ',')

/** Nota sobre 10; en vermell si no arriba a l'aprovat. */
function Nota({ valor, forta = false }: { valor: number | null; forta?: boolean }) {
  const suspesa = valor != null && valor < NOTA_APROVAT
  return (
    <span className={`tabular-nums ${forta ? 'font-semibold' : ''} ${valor == null ? 'text-gray-300' : suspesa ? 'text-red-600 font-semibold' : 'text-gray-800'}`}>
      {fmt(valor)}
    </span>
  )
}

/** Notes d'un examen: una fila per alumne amb la nota de l'examen i la de cada competència, sobre 10. */
export default function NotesPage() {
  const { examId } = useParams<{ examId: string }>()
  const [exam, setExam] = useState<Exam | null>(null)
  const [sessions, setSessions] = useState<Session[] | null>(null)
  const [error, setError] = useState('')
  const [nomesEntregats, setNomesEntregats] = useState(false)
  const [avis, setAvis] = useState('')
  const [eliminant, setEliminant] = useState<string | null>(null)
  const [ordre, setOrdre] = useState<{ per: ColumnaOrdre; descendent: boolean }>({ per: 'alumne', descendent: false })

  useEffect(() => {
    if (!examId) return
    Promise.all([getExam(examId), getSessionsByExam(examId)])
      .then(([e, ss]) => { setExam(e); setSessions(ss) })
      .catch(err => setError(err?.response?.data?.error || 'No s\'han pogut carregar les notes'))
  }, [examId])

  const taula = useMemo(() => exam && sessions ? taulaNotes(exam, sessions) : null, [exam, sessions])
  const files = useMemo(() => {
    if (!taula) return []
    const visibles = nomesEntregats ? taula.files.filter(f => f.entregat) : taula.files
    return ordenaFiles(visibles, ordre.per, ordre.descendent)
  }, [taula, nomesEntregats, ordre])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!exam || !taula) return <Layout><p className="text-gray-400 text-sm">Carregant…</p></Layout>

  const elimina = async (s: Session) => {
    if (!confirm(`Eliminar ${s.studentName} d'aquest examen?\n\nS'esborraran totes les seves respostes, notes i fitxers lliurats. No es pot desfer.`)) return
    setEliminant(s.id)
    setAvis('')
    try {
      await deleteSession(s.id)
      setSessions(prev => prev ? prev.filter(x => x.id !== s.id) : prev)
    } catch (err: any) {
      setAvis(err?.response?.data?.error || 'No s\'ha pogut eliminar l\'alumne')
    } finally {
      setEliminant(null)
    }
  }

  const ordena = (per: ColumnaOrdre) =>
    setOrdre(o => o.per === per ? { per, descendent: !o.descendent } : { per, descendent: false })
  const fletxa = (per: ColumnaOrdre) => ordre.per === per ? (ordre.descendent ? ' ▾' : ' ▴') : ''

  const exporta = () => {
    const blob = new Blob([csvTaulaNotes(taula.competencies, files)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = nomExportacio(exam.title, 'notes-i-competencies.csv')
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  const Cap = ({ per, children, className = '' }: { per: ColumnaOrdre; children: React.ReactNode; className?: string }) => (
    <th scope="col" className={`px-3 py-2 font-semibold text-xs text-gray-600 whitespace-nowrap ${className}`}>
      <button type="button" onClick={() => ordena(per)} className="hover:text-brand-700">{children}{fletxa(per)}</button>
    </th>
  )

  const entregats = taula.files.filter(f => f.entregat).length
  const hiHaProvisionals = files.some(f => f.pendents > 0)

  return (
    <Layout>
      <div className="space-y-4">
        <div className="flex items-start justify-between gap-3 flex-wrap">
          <div>
            <Link to="/professor/exams" className="text-xs text-brand-600 hover:underline">← Els meus exàmens</Link>
            <h1 className="text-2xl font-bold text-brand-700">Notes · {exam.title}</h1>
            <p className="text-sm text-gray-500">
              {entregats} de {taula.files.length} alumnes han entregat · notes sobre 10, en vermell les inferiors a {NOTA_APROVAT}
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <Link to={`/professor/exams/${exam.id}/corrections`}
              className="text-xs border border-brand-600 text-brand-600 px-3 py-1.5 rounded-lg hover:bg-brand-50">Correccions</Link>
            <button type="button" onClick={exporta} disabled={files.length === 0}
              className="text-xs bg-blue-600 text-white px-3 py-1.5 rounded-lg hover:bg-blue-700 disabled:opacity-40">
              Exportar aquesta taula (CSV)
            </button>
          </div>
        </div>

        <label className="inline-flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
          <input type="checkbox" checked={nomesEntregats} onChange={e => setNomesEntregats(e.target.checked)}
            className="accent-brand-600 w-4 h-4" />
          Mostra només els alumnes que han entregat
        </label>

        {avis && <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2">{avis}</p>}

        {taula.competencies.length === 0 && (
          <p className="text-xs text-gray-500 bg-gray-50 border border-gray-200 rounded px-3 py-2">
            Cap pregunta d'aquest examen té competència, així que només es mostra la nota de l'examen.
          </p>
        )}

        {files.length === 0 ? (
          <p className="text-gray-400 text-sm">{taula.files.length === 0 ? 'Cap alumne té sessió en aquest examen.' : 'Cap alumne ha entregat encara.'}</p>
        ) : (
          <div className="bg-white border border-gray-200 rounded-xl overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <Cap per="alumne" className="text-left">Alumne</Cap>
                  <th scope="col" className="px-3 py-2 font-semibold text-xs text-gray-600 text-left">Estat</th>
                  <Cap per="nota" className="text-right border-l border-gray-200">Nota de l'examen</Cap>
                  {taula.competencies.map(co => (
                    <Cap key={co} per={co} className="text-right">
                      <span title={exam.competencies?.find(c => c.codi === co)?.descripcio || undefined}>{co}</span>
                    </Cap>
                  ))}
                  <Cap per="pendents" className="text-right border-l border-gray-200">Pendents</Cap>
                  <th scope="col" className="px-3 py-2"><span className="sr-only">Accions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {files.map(f => (
                  <tr key={f.sessio.id} className={f.entregat ? '' : 'text-gray-500'}>
                    <td className="px-3 py-1.5 whitespace-nowrap">{f.sessio.studentName}</td>
                    <td className="px-3 py-1.5 text-xs">{f.estat}</td>
                    <td className="px-3 py-1.5 text-right border-l border-gray-100"><Nota valor={f.nota} forta /></td>
                    {taula.competencies.map(co => (
                      <td key={co} className="px-3 py-1.5 text-right"><Nota valor={f.competencies[co]} /></td>
                    ))}
                    <td className="px-3 py-1.5 text-right border-l border-gray-100 tabular-nums text-xs">
                      {f.estat === 'No presentat' ? '' : f.pendents > 0 ? <span className="text-amber-700">{f.pendents}</span> : '0'}
                    </td>
                    <td className="px-3 py-1.5 text-right whitespace-nowrap">
                      <button type="button" onClick={() => elimina(f.sessio)} disabled={eliminant !== null}
                        title="Elimina l'alumne d'aquest examen amb totes les seves dades"
                        className="text-xs text-red-600 border border-red-200 px-2 py-0.5 rounded hover:bg-red-50 disabled:opacity-40">
                        {eliminant === f.sessio.id ? 'Eliminant…' : 'Eliminar'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {hiHaProvisionals && (
          <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2">
            Provisional: hi ha respostes sense qualificar (columna «Pendents»); compten 0 fins que es corregeixin.
          </p>
        )}
      </div>
    </Layout>
  )
}

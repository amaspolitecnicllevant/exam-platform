import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getExam, updateExamSettings, treuPesosParts } from '../../api/exams'
import PesosParts from '../../components/PesosParts'
import { getSessionsByExam, deleteSession } from '../../api/sessions'
import type { Exam, Session } from '../../types'
import { taulaNotes, ordenaFiles, csvTaulaNotes, NOTA_APROVAT, type ColumnaOrdre, type NotaCompetencia } from '../../utils/taulaNotes'
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

/** Punts reals d'una competència («1,50 / 3»); en vermell si la nota equivalent sobre 10 és inferior a 5. */
function PuntsCompetencia({ valor }: { valor: NotaCompetencia | null }) {
  if (!valor) return <span className="text-gray-300">—</span>
  const suspesa = valor.sobreDeu != null && valor.sobreDeu < NOTA_APROVAT
  return (
    <span className={`tabular-nums ${suspesa ? 'text-red-600 font-semibold' : 'text-gray-800'}`}
      title={valor.sobreDeu != null ? `Equival a ${fmt(valor.sobreDeu)} sobre 10` : undefined}>
      {fmt(valor.punts)}<span className="text-xs font-normal text-gray-400"> / {fmt(valor.maxim)}</span>
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
  const [mostraPesos, setMostraPesos] = useState(false)
  const [treuPenalitzant, setTreuPenalitzant] = useState(false)
  const [tornant, setTornant] = useState(false)
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

  const teParts = exam.questions.some(q => q.tipus === 'SECTION')
  const tornaALsInicials = async () => {
    if (!confirm(`Tornar als valors inicials de l'examen (sense pesos per part i amb la penalització original, ${Math.round((exam.penalitzacioOriginal ?? exam.penalitzacioChoice) * 100)} %)?\n\nEs recalcularan les notes de tots els alumnes${exam.notesVisibles ? ', també les ja publicades' : ''}.`)) return
    setTornant(true)
    setAvis('')
    try {
      let e = exam
      if (exam.questions.some(q => q.pesPart != null)) e = await treuPesosParts(exam.id)
      if (Math.abs((exam.penalitzacioOriginal ?? exam.penalitzacioChoice) - exam.penalitzacioChoice) > 1e-6) {
        e = await updateExamSettings(exam.id, { penalitzacioChoice: exam.penalitzacioOriginal })
      }
      setExam(e)
      await recarregaSessions()
    } catch (err: any) {
      setAvis(err?.response?.data?.error || 'No s\'ha pogut tornar als valors inicials')
    } finally {
      setTornant(false)
    }
  }

  const recarregaSessions = () => getSessionsByExam(exam.id).then(setSessions)

  const llevaPenalitzacio = async () => {
    const pen = Math.round(exam.penalitzacioChoice * 100)
    if (!confirm(`Llevar la penalització del test (ara −${pen} % del valor per resposta incorrecta)?\n\nEs re-corregiran les respostes de test de tots els alumnes i les notes canviaran${exam.notesVisibles ? ', també les ja publicades' : ''}.`)) return
    setTreuPenalitzant(true)
    setAvis('')
    try {
      setExam(await updateExamSettings(exam.id, { penalitzacioChoice: 0 }))
      await recarregaSessions()
    } catch (err: any) {
      setAvis(err?.response?.data?.error || 'No s\'ha pogut llevar la penalització')
    } finally {
      setTreuPenalitzant(false)
    }
  }

  const exporta = () => {
    const blob = new Blob([csvTaulaNotes(taula.competencies, files, taula.parts, taula.modificada)], { type: 'text/csv;charset=utf-8' })
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
              {entregats} de {taula.files.length} alumnes han entregat · nota de l'examen sobre 10; entre parèntesis, la nota original quan s'han canviat pesos o penalització; competències i parts en punts reals (/ màxim); en vermell si equival a menys de {NOTA_APROVAT} sobre 10
            </p>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <button type="button" onClick={() => setMostraPesos(m => !m)} disabled={!teParts}
              title={teParts ? 'Veure els pesos actuals de cada part i posar-ne de nous' : 'Aquest examen no té parts (seccions ###)'}
              className="text-xs border border-amber-500 text-amber-700 px-3 py-1.5 rounded-lg hover:bg-amber-50 disabled:opacity-40 disabled:hover:bg-transparent">
              Canvia els pesos de les parts
            </button>
            <button type="button" onClick={llevaPenalitzacio} disabled={treuPenalitzant || exam.penalitzacioChoice <= 0}
              title={exam.penalitzacioChoice > 0 ? 'Treu la penalització de les respostes incorrectes del test i recalcula les notes' : 'Aquest examen ja no té penalització'}
              className="text-xs border border-amber-500 text-amber-700 px-3 py-1.5 rounded-lg hover:bg-amber-50 disabled:opacity-40 disabled:hover:bg-transparent">
              {treuPenalitzant ? 'Recalculant…' : 'Lleva la penalització del test'}
            </button>
            <button type="button" onClick={tornaALsInicials} disabled={tornant || !taula.modificada}
              title={taula.modificada ? 'Treu els pesos per part i recupera la penalització original' : 'L\'examen encara té els valors inicials'}
              className="text-xs border border-gray-400 text-gray-700 px-3 py-1.5 rounded-lg hover:bg-gray-50 disabled:opacity-40 disabled:hover:bg-transparent">
              {tornant ? 'Recalculant…' : 'Torna als valors inicials'}
            </button>
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

        {mostraPesos && teParts && (
          <PesosParts key="pesos" exam={exam} obertInicial
            onCanvi={e => { setExam(e); recarregaSessions() }} />
        )}

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
                      <span>{co}</span>
                    </Cap>
                  ))}
                  {taula.parts.map((p, i) => (
                    <Cap key={p.clau} per={p.clau} className={`text-right ${i === 0 ? 'border-l border-gray-200' : ''}`}>
                      <span title={p.titol} className="inline-block max-w-[7rem] truncate align-bottom">{p.titol}</span>
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
                    <td className="px-3 py-1.5 text-right border-l border-gray-100 whitespace-nowrap">
                      <Nota valor={f.nota} forta />
                      {taula.modificada && f.nota != null && f.notaOriginal != null && Math.abs(f.nota - f.notaOriginal) >= 0.005 && (
                        <span className="text-xs text-gray-400 tabular-nums ml-1" title="Nota original, amb els valors inicials de l'examen">({fmt(f.notaOriginal)})</span>
                      )}
                    </td>
                    {taula.competencies.map(co => (
                      <td key={co} className="px-3 py-1.5 text-right"><PuntsCompetencia valor={f.competencies[co]} /></td>
                    ))}
                    {taula.parts.map((p, i) => (
                      <td key={p.clau} className={`px-3 py-1.5 text-right ${i === 0 ? 'border-l border-gray-100' : ''}`}><PuntsCompetencia valor={f.parts[p.clau]} /></td>
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

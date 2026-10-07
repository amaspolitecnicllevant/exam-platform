import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getMyExams, publishExam, unpublishExam, closeExam, deleteExam, duplicateExam, scheduleExam, unscheduleExam, assignModul, publicarNotes, ocultarNotes } from '../../api/exams'
import { getGrups, assignExamToGrup } from '../../api/grups'
import { getModuls } from '../../api/moduls'
import { getAules, assignAulaExamen, removeAulaExamen } from '../../api/aules'
import FiltreGrups from '../../components/FiltreGrups'
import AlumnesExamen from '../../components/AlumnesExamen'
import {
  FILTRE_GRUPS_BUIT, filtraGrupsAssignacio, filtreGrupsInicial, type FiltreGrupsAssig,
} from '../../utils/filtreGrupsAssignacio'
import ExportarExamen from '../../components/ExportarExamen'
import type { Exam, Grup, Modul, Aula } from '../../types'
import { aInstantUtc, dataLocal, horaLocal } from '../../dates'
import {
  SENSE_MODUL, FILTRE_EXAMENS_BUIT, filtraExamens, hiHaFiltres, opcionsFiltre,
  type FiltreExamens,
} from '../../utils/filtreExamens'

function examVisualState(exam: Exam): 'scheduled' | 'active' | 'closed' | 'draft' {
  if (exam.status === 'CLOSED') return 'closed'
  if (exam.status === 'PUBLISHED') return 'active'
  if (exam.status === 'DRAFT' && exam.scheduledAt) return 'scheduled'
  return 'draft'
}

const CARD_STYLES: Record<string, string> = {
  draft:     'bg-white border-l-4 border-l-gray-300',
  scheduled: 'bg-blue-50 border-l-4 border-l-blue-400',
  active:    'bg-green-50 border-l-4 border-l-green-500',
  closed:    'bg-gray-50 border-l-4 border-l-gray-400 opacity-75',
}

const STATUS_BADGE: Record<string, string> = {
  draft:     'bg-gray-100 text-gray-600',
  scheduled: 'bg-blue-100 text-blue-700',
  active:    'bg-green-100 text-green-800',
  closed:    'bg-gray-200 text-gray-500',
}

const STATUS_LABELS: Record<string, string> = {
  draft:     'Esborrany',
  scheduled: 'Programat',
  active:    'En curs',
  closed:    'Finalitzat',
}

export default function ExamsPage() {
  const [exams, setExams]   = useState<Exam[]>([])
  const [grups, setGrups]   = useState<Grup[]>([])
  const [moduls, setModuls]         = useState<Modul[]>([])
  const [aules, setAules]           = useState<Aula[]>([])
  const [assigning, setAssigning]   = useState<Exam | null>(null)
  // activar per a tots o per a alumnes concrets, i gestionar-los després
  const [audiencia, setAudiencia]   = useState<{ exam: Exam; mode: 'activar' | 'gestionar' } | null>(null)
  const [scheduling, setScheduling] = useState<Exam | null>(null)
  const [schedDate, setSchedDate]   = useState('')
  const [schedTime, setSchedTime]   = useState('')
  const [schedGrup, setSchedGrup]   = useState('')
  const [feedback, setFeedback]     = useState('')
  const [schedError, setSchedError] = useState('')
  const [assigningModul, setAssigningModul] = useState<Exam | null>(null)
  const [selectedModul, setSelectedModul]   = useState('')
  const [assigningAula, setAssigningAula]   = useState<Exam | null>(null)
  const [selectedAula, setSelectedAula]     = useState('')
  const [expandedConfig, setExpandedConfig] = useState<Set<string>>(new Set())
  const [filtre, setFiltre]     = useState<FiltreExamens>(FILTRE_EXAMENS_BUIT)
  const canviaFiltre = (canvis: Partial<FiltreExamens>) => setFiltre(prev => ({ ...prev, ...canvis }))
  // Filtres per triar grup (assignar o programar un examen)
  const [filtreGrups, setFiltreGrups] = useState<FiltreGrupsAssig>(FILTRE_GRUPS_BUIT)

  const toggleConfig = (id: string) =>
    setExpandedConfig(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })

  useEffect(() => {
    getMyExams().then(setExams)
    getGrups().then(setGrups)
    getModuls().then(setModuls)
    getAules().then(setAules)
  }, [])

  const refresh = () => getMyExams().then(setExams)

  const [duplicant, setDuplicant] = useState<string | null>(null)
  const handleDuplicate = async (exam: Exam) => {
    setDuplicant(exam.id)
    try {
      const copia = await duplicateExam(exam.id)
      setFiltre(FILTRE_EXAMENS_BUIT)
      await refresh()
      setFeedback(`S'ha creat «${copia.title}» com a esborrany. Pots canviar-li el títol i la durada a Previsualitzar.`)
    } catch (err: any) {
      setFeedback(err?.response?.data?.error || 'Error duplicant l\'examen')
    } finally {
      setDuplicant(null)
    }
  }


  const openSchedule = (exam: Exam) => {
    setScheduling(exam)
    setSchedError('')
    setFiltreGrups(filtreGrupsInicial(exam.modulId, grups, moduls))
    if (exam.scheduledAt) {
      const d = new Date(exam.scheduledAt)
      setSchedDate(dataLocal(d))
      setSchedTime(horaLocal(d))
      setSchedGrup(exam.scheduledGrupId ?? '')
    } else {
      setSchedDate(''); setSchedTime(''); setSchedGrup('')
    }
  }

  const handleScheduleSave = async () => {
    if (!scheduling || !schedDate || !schedTime || !schedGrup) return
    setSchedError('')
    try {
      await scheduleExam(scheduling.id, aInstantUtc(schedDate, schedTime), schedGrup)
      setScheduling(null)
      refresh()
    } catch (err: any) {
      setSchedError(err.response?.data?.error || err.response?.data?.message || err.message || 'Error en programar')
    }
  }

  const handlePublicarNotes = async (exam: Exam) => {
    try {
      const updated = await publicarNotes(exam.id)
      setExams(prev => prev.map(x => x.id === updated.id ? updated : x))
    } catch (err: any) {
      // p. ex. 409 si hi ha respostes pendents de revisar
      setFeedback(err.response?.data?.error || 'Error publicant les notes')
    }
  }

  const handleUnschedule = async (exam: Exam) => {
    await unscheduleExam(exam.id)
    refresh()
  }

  const handleAssign = async (grupId: string) => {
    if (!assigning) return
    try {
      const result = await assignExamToGrup(grupId, assigning.id)
      setFeedback(result.missatge)
      setAssigning(null)
    } catch {
      setFeedback('Error assignant l\'examen')
    }
  }

  const handleAssignModul = async () => {
    if (!assigningModul || !selectedModul) return
    try {
      const updated = await assignModul(assigningModul.id, selectedModul)
      setExams(prev => prev.map(e => e.id === updated.id ? updated : e))
      setAssigningModul(null)
    } catch {
      setFeedback('Error assignant el mòdul')
    }
  }

  const handleAssignAula = async () => {
    if (!assigningAula) return
    try {
      const updated = selectedAula
        ? await assignAulaExamen(assigningAula.id, selectedAula)
        : await removeAulaExamen(assigningAula.id)
      setExams(prev => prev.map(e => e.id === updated.id ? updated : e))
      setAssigningAula(null)
    } catch {
      setFeedback('Error assignant l\'aula')
    }
  }

  const opcions = opcionsFiltre(exams)
  const grupsVisibles = filtraGrupsAssignacio(grups, moduls, filtreGrups)
  // El grup ja triat a «Programar» es manté al desplegable encara que el filtre l'amagui
  const grupTriat = grups.find(g => g.id === schedGrup)
  const grupsSelect = grupTriat && !grupsVisibles.some(g => g.id === grupTriat.id) ? [...grupsVisibles, grupTriat] : grupsVisibles
  const filtresActius = hiHaFiltres(filtre)
  const filtrats = filtraExamens(exams, filtre, examVisualState)

  return (
    <Layout>
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-brand-700">Els meus examens</h1>
          <div className="flex gap-2">
            <Link to="/professor/grups"
              className="border border-brand-600 text-brand-600 px-4 py-2 rounded-lg text-sm hover:bg-brand-50">
              Grups
            </Link>
            <Link to="/professor/exams/new"
              className="bg-brand-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-brand-700">
              + Nou examen (MD)
            </Link>
          </div>
        </div>

        {feedback && (
          <div className="bg-green-50 border border-green-200 text-green-700 rounded-lg px-4 py-2 text-sm flex justify-between">
            {feedback}
            <button onClick={() => setFeedback('')} className="ml-4 font-bold">×</button>
          </div>
        )}

        {exams.length > 0 && (
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <input type="search" value={filtre.cerca} onChange={e => canviaFiltre({ cerca: e.target.value })}
                placeholder="Cerca per títol…" aria-label="Cerca per títol"
                className="border rounded-lg px-3 py-1.5 text-sm flex-1 min-w-[12rem]" />
              <select value={filtre.estat} onChange={e => canviaFiltre({ estat: e.target.value })} aria-label="Estat"
                className="border rounded-lg px-2 py-1.5 text-sm">
                <option value="">Tots els estats</option>
                {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              {opcions.autors.length > 1 && (
                <select value={filtre.autor} onChange={e => canviaFiltre({ autor: e.target.value })} aria-label="Professor"
                  className="border rounded-lg px-2 py-1.5 text-sm max-w-[14rem]">
                  <option value="">Tots els professors</option>
                  {opcions.autors.map(a => <option key={a} value={a}>{a}</option>)}
                </select>
              )}
              {opcions.cicles.length > 0 && (
                <select value={filtre.cicle} onChange={e => canviaFiltre({ cicle: e.target.value })} aria-label="Cicle"
                  className="border rounded-lg px-2 py-1.5 text-sm max-w-[14rem]">
                  <option value="">Tots els cicles</option>
                  {opcions.cicles.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              )}
              {opcions.moduls.length > 0 && (
                <select value={filtre.modul} onChange={e => canviaFiltre({ modul: e.target.value })} aria-label="Mòdul"
                  className="border rounded-lg px-2 py-1.5 text-sm max-w-[16rem]">
                  <option value="">Tots els mòduls</option>
                  {opcions.moduls.map(m => <option key={m} value={m}>{m}</option>)}
                  <option value={SENSE_MODUL}>Sense mòdul</option>
                </select>
              )}
              {opcions.grups.length > 0 && (
                <select value={filtre.grup} onChange={e => canviaFiltre({ grup: e.target.value })}
                  aria-label="Grup programat" title="Grup per al qual l'examen està programat"
                  className="border rounded-lg px-2 py-1.5 text-sm max-w-[14rem]">
                  <option value="">Tots els grups</option>
                  {opcions.grups.map(g => <option key={g.id} value={g.id}>{g.nom}</option>)}
                </select>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2 text-sm text-gray-600">
              <span title="Data programada de l'examen, o la de creació si no està programat">Data:</span>
              <label className="flex items-center gap-1">des de
                <input type="date" value={filtre.dataDes} max={filtre.dataFins || undefined}
                  onChange={e => canviaFiltre({ dataDes: e.target.value })}
                  className="border rounded-lg px-2 py-1 text-sm" />
              </label>
              <label className="flex items-center gap-1">fins a
                <input type="date" value={filtre.dataFins} min={filtre.dataDes || undefined}
                  onChange={e => canviaFiltre({ dataFins: e.target.value })}
                  className="border rounded-lg px-2 py-1 text-sm" />
              </label>
              {filtresActius && (
                <>
                  <span className="text-xs text-gray-500 ml-2">{filtrats.length} de {exams.length}</span>
                  <button onClick={() => setFiltre(FILTRE_EXAMENS_BUIT)}
                    className="text-xs text-brand-600 hover:underline">Treu els filtres</button>
                </>
              )}
            </div>
          </div>
        )}

        <div className="grid gap-4">
          {filtrats.map(exam => {
            const isConfigOpen = expandedConfig.has(exam.id)
            return (
              <div key={exam.id} className={`rounded-xl border overflow-hidden ${CARD_STYLES[examVisualState(exam)]}`}>

                {/* Fila principal */}
                <div className="p-5 flex items-start justify-between gap-4">
                  {/* Info */}
                  <div className="min-w-0">
                    <div className="flex items-center gap-3 mb-1 flex-wrap">
                      <h2 className="font-semibold text-lg">{exam.title}</h2>
                      <span className={`text-xs px-2 py-0.5 rounded-full flex items-center gap-1 ${STATUS_BADGE[examVisualState(exam)]}`}>
                        {exam.status === 'PUBLISHED' && (
                          <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse inline-block" />
                        )}
                        {STATUS_LABELS[examVisualState(exam)]}
                      </span>
                      {exam.restringit && exam.status !== 'CLOSED' && (
                        <span className="text-xs bg-teal-100 text-teal-800 px-2 py-0.5 rounded-full" title="Només els alumnes assignats hi poden entrar">
                          Alumnes concrets
                        </span>
                      )}
                      {exam.scheduledAt && exam.status === 'DRAFT' && (
                        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">
                          {new Date(exam.scheduledAt).toLocaleString('ca-ES', {dateStyle:'short',timeStyle:'short'})} · {exam.scheduledGrupName}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-1 flex-wrap">
                      <p className="text-sm text-gray-500">{exam.questions.length} preguntes · {exam.durada} min</p>
                      {exam.modulNom && (
                        <span className="text-xs bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full font-mono">
                          {exam.modulNom}
                        </span>
                      )}
                      {exam.cicleNom && (
                        <span className="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full">
                          {exam.cicleNom}
                        </span>
                      )}
                      {exam.aulaNom && (
                        <span className="text-xs bg-orange-100 text-orange-700 px-2 py-0.5 rounded-full font-mono">
                          {exam.aulaNom} ({exam.aulaCidr})
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Accions principals */}
                  <div className="flex gap-2 flex-wrap justify-end items-center shrink-0">
                    <Link to={`/professor/exams/${exam.id}/preview`}
                      className="text-xs border border-gray-300 text-gray-600 px-3 py-1 rounded hover:bg-gray-50">
                      Previsualitzar
                    </Link>
                    <button onClick={() => handleDuplicate(exam)} disabled={duplicant !== null}
                      title="Crea una còpia en esborrany amb les mateixes preguntes, configuració i fitxers"
                      className="text-xs border border-gray-300 text-gray-600 px-3 py-1 rounded hover:bg-gray-50 disabled:opacity-50">
                      {duplicant === exam.id ? 'Duplicant…' : 'Duplicar'}
                    </button>

                    {exam.status === 'PUBLISHED' && (
                      <>
                        <Link to={`/professor/exams/${exam.id}/monitor`}
                          className="text-xs bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700">
                          Monitor
                        </Link>
                        <Link to={`/professor/exams/${exam.id}/corrections`}
                          className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700">
                          Correccions
                        </Link>
                        <Link to={`/professor/exams/${exam.id}/stats`}
                          className="text-xs border border-brand-600 text-brand-600 px-3 py-1 rounded hover:bg-brand-50">
                          Estadístiques
                        </Link>
                        <button onClick={() => unpublishExam(exam.id).then(refresh)}
                          className="text-xs bg-orange-500 text-white px-3 py-1 rounded hover:bg-orange-600 flex items-center gap-1">
                          <span className="w-2 h-2 rounded-full bg-white inline-block animate-pulse"></span>Desactivar
                        </button>
                        <button onClick={() => { if(confirm('Tancar definitivament? No es podrà reactivar. Els alumnes que l\'estiguin fent l\'entregaran tal com el tinguin.')) closeExam(exam.id).then(refresh) }}
                          className="text-xs bg-gray-500 text-white px-3 py-1 rounded hover:bg-gray-600">
                          Tancar
                        </button>
                      </>
                    )}

                    {exam.status === 'DRAFT' && (
                      <button onClick={() => {
                          if (exam.scheduledAt) { publishExam(exam.id).then(refresh); return }
                          // Amb destinataris ja triats a l'esborrany, s'activa directament per a ells
                          if (exam.restringit) {
                            if (confirm('Activar l\'examen només per als alumnes triats?')) publishExam(exam.id).then(refresh).catch(e => alert(e?.response?.data?.error || 'No s\'ha pogut activar'))
                            return
                          }
                          setAudiencia({ exam, mode: 'activar' })
                        }}
                        className="text-xs bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700 flex items-center gap-1">
                        <span className="w-2 h-2 rounded-full bg-white/70 inline-block"></span>Activar
                      </button>
                    )}

                    {exam.status === 'CLOSED' && (
                      <>
                        <Link to={`/professor/exams/${exam.id}/corrections`}
                          className="text-xs bg-brand-600 text-white px-3 py-1 rounded hover:bg-brand-700">
                          Correccions
                        </Link>
                        <Link to={`/professor/exams/${exam.id}/stats`}
                          className="text-xs border border-brand-600 text-brand-600 px-3 py-1 rounded hover:bg-brand-50">
                          Estadístiques
                        </Link>
                        <ExportarExamen examId={exam.id} titol={exam.title}
                          teFitxers={!!exam.questions?.some(q => q.tipus === 'FILE_UPLOAD')} />
                      </>
                    )}

                    {/* Botó de configuració */}
                    <button
                      onClick={() => toggleConfig(exam.id)}
                      title="Configurar examen"
                      className={`text-xs border px-3 py-1 rounded flex items-center gap-1 transition-colors ${
                        isConfigOpen
                          ? 'bg-gray-100 border-gray-400 text-gray-700'
                          : 'border-gray-300 text-gray-500 hover:bg-gray-50 hover:text-gray-700'
                      }`}>
                      ⚙ {isConfigOpen ? '▲' : '▼'}
                    </button>
                  </div>
                </div>

                {/* Panell de configuració (col·lapsable) */}
                {isConfigOpen && (
                  <div className="px-5 py-3 border-t border-gray-200 bg-gray-50/70 flex gap-2 flex-wrap items-center">

                    {/* Mòdul */}
                    <button onClick={() => { setAssigningModul(exam); setSelectedModul(exam.modulId ?? '') }}
                      className="text-xs border border-indigo-200 text-indigo-600 px-3 py-1 rounded hover:bg-indigo-50">
                      {exam.modulNom ? `Mòdul: ${exam.modulNom}` : 'Assignar mòdul…'}
                    </button>

                    {/* Aula */}
                    <button onClick={() => { setAssigningAula(exam); setSelectedAula(exam.aulaId ?? '') }}
                      className="text-xs border border-orange-200 text-orange-600 px-3 py-1 rounded hover:bg-orange-50">
                      {exam.aulaNom ? `Aula: ${exam.aulaNom}` : 'Restricció d\'aula…'}
                    </button>

                    {/* Programar (només esborrany) */}
                    {exam.status === 'DRAFT' && (
                      <>
                        <button onClick={() => openSchedule(exam)}
                          className="text-xs border border-blue-300 text-blue-600 px-3 py-1 rounded hover:bg-blue-50">
                          {exam.scheduledAt ? '⏰ Editar programació' : '⏰ Programar'}
                        </button>
                        {exam.scheduledAt && (
                          <button onClick={() => handleUnschedule(exam)}
                            className="text-xs border border-gray-300 text-gray-500 px-3 py-1 rounded hover:bg-gray-100">
                            Anul·lar programació
                          </button>
                        )}
                      </>
                    )}

                    {/* Destinataris: a l'esborrany es trien abans d'activar; en un examen actiu restringit es poden ampliar */}
                    {((exam.status === 'DRAFT' && !exam.scheduledAt) || (exam.status === 'PUBLISHED' && exam.restringit)) && (
                      <button onClick={() => setAudiencia({ exam, mode: 'gestionar' })}
                        className="text-xs bg-teal-600 text-white px-3 py-1 rounded hover:bg-teal-700">
                        {exam.status === 'DRAFT' ? 'Destinataris' : 'Alumnes'}
                      </button>
                    )}

                    {/* Assignar a grup (publicat) */}
                    {exam.status === 'PUBLISHED' && !exam.restringit && (
                      <button onClick={() => { setFiltreGrups(filtreGrupsInicial(exam.modulId, grups, moduls)); setAssigning(exam) }}
                        className="text-xs bg-indigo-600 text-white px-3 py-1 rounded hover:bg-indigo-700">
                        Assignar a grup
                      </button>
                    )}

                    {/* Publicar / ocultar notes (exàmens actius o tancats) */}
                    {(exam.status === 'PUBLISHED' || exam.status === 'CLOSED') && (
                      exam.notesVisibles ? (
                        <button onClick={() => ocultarNotes(exam.id).then(e => setExams(prev => prev.map(x => x.id === e.id ? e : x)))}
                          className="text-xs border border-amber-300 text-amber-700 px-3 py-1 rounded hover:bg-amber-50">
                          Ocultar notes als alumnes
                        </button>
                      ) : (
                        <button onClick={() => handlePublicarNotes(exam)}
                          className="text-xs bg-teal-600 text-white px-3 py-1 rounded hover:bg-teal-700">
                          Publicar notes als alumnes
                        </button>
                      )
                    )}

                    <span className="flex-1" />

                    {/* Eliminar */}
                    <button onClick={() => { if(confirm('Eliminar?')) deleteExam(exam.id).then(refresh) }}
                      className="text-xs text-red-600 border border-red-200 px-3 py-1 rounded hover:bg-red-50">
                      Eliminar
                    </button>
                  </div>
                )}
              </div>
            )
          })}
          {exams.length === 0 && <p className="text-gray-400 text-sm">Cap examen. Crea'n un des d'un fitxer .md</p>}
          {exams.length > 0 && filtrats.length === 0 && <p className="text-gray-400 text-sm">Cap examen coincideix amb els filtres.</p>}
        </div>
      </div>

      {/* Modal programar examen */}
      {scheduling && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Programar «{scheduling.title}»</h2>
              <p className="text-xs text-gray-500 mt-0.5">L'examen s'activarà automàticament a l'hora indicada i s'assignarà al grup.</p>
            </div>
            <div className="px-6 py-4 space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Data</label>
                  <input type="date" value={schedDate} onChange={e => setSchedDate(e.target.value)}
                    min={dataLocal(new Date())}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-400" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Hora</label>
                  <input type="time" value={schedTime} onChange={e => setSchedTime(e.target.value)}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-400" />
                </div>
              </div>
              <div className="space-y-2">
                <label className="block text-xs font-medium text-gray-600">Grup</label>
                <FiltreGrups grups={grups} moduls={moduls} filtre={filtreGrups}
                  onChange={setFiltreGrups} visibles={grupsVisibles.length} />
                <select value={schedGrup} onChange={e => setSchedGrup(e.target.value)}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-400">
                  <option value="">— Selecciona un grup —</option>
                  {grupsSelect.map(g => (
                    <option key={g.id} value={g.id}>
                      {g.name}{g.modulNom ? ` · ${g.modulNom}` : ''} ({g.students.length} alumnes)
                    </option>
                  ))}
                </select>
              </div>
              {schedError && (
                <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2 text-xs text-red-700">
                  {schedError}
                </div>
              )}
            </div>
            <div className="px-6 py-4 border-t border-gray-100 flex justify-between">
              <button onClick={() => setScheduling(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
              <button onClick={handleScheduleSave}
                disabled={!schedDate || !schedTime || !schedGrup}
                className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700 disabled:opacity-50">
                Guardar programació
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal assignar mòdul */}
      {assigningModul && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Assignar mòdul a «{assigningModul.title}»</h2>
              <p className="text-xs text-gray-500 mt-0.5">Filtra la visibilitat als alumnes matriculats en aquest mòdul.</p>
            </div>
            <div className="px-6 py-4">
              <select value={selectedModul} onChange={e => setSelectedModul(e.target.value)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-brand-400">
                <option value="">— Sense mòdul (visible a tothom) —</option>
                {moduls.map(m => (
                  <option key={m.id} value={m.id}>{m.codi} — {m.nom} ({m.cicleNom})</option>
                ))}
              </select>
            </div>
            <div className="px-6 py-4 border-t border-gray-100 flex justify-between">
              <button onClick={() => setAssigningModul(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
              <button onClick={handleAssignModul}
                className="bg-brand-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-brand-700">
                Assignar
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal assignar aula */}
      {assigningAula && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Restricció d'aula per «{assigningAula.title}»</h2>
              <p className="text-xs text-gray-500 mt-0.5">
                Si selecciones una aula, l'examen només es podrà fer des de màquines de la seva subxarxa.
              </p>
            </div>
            <div className="px-6 py-4">
              <select value={selectedAula} onChange={e => setSelectedAula(e.target.value)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-400">
                <option value="">— Sense restricció d'aula —</option>
                {aules.map(a => (
                  <option key={a.id} value={a.id}>{a.nom} — {a.xarxaCidr}</option>
                ))}
              </select>
            </div>
            <div className="px-6 py-4 border-t border-gray-100 flex justify-between">
              <button onClick={() => setAssigningAula(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
              <button onClick={handleAssignAula}
                className="bg-orange-600 text-white rounded-lg px-4 py-2 text-sm hover:bg-orange-700">
                Desar
              </button>
            </div>
          </div>
        </div>
      )}

      {audiencia && (
        <AlumnesExamen exam={audiencia.exam} grups={grups} mode={audiencia.mode}
          onClose={() => setAudiencia(null)} onFet={refresh} />
      )}

      {/* Modal assignar a grup */}
      {assigning && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md">
            <div className="px-6 py-4 border-b border-gray-100">
              <h2 className="font-semibold text-gray-800">Assignar «{assigning.title}» a un grup</h2>
              <p className="text-xs text-gray-500 mt-0.5">Es crearà una sessió per a cada alumne del grup que no en tingui ja una.</p>
            </div>
            {grups.length > 0 && (
              <div className="px-4 pt-3">
                <FiltreGrups grups={grups} moduls={moduls} filtre={filtreGrups}
                  onChange={setFiltreGrups} visibles={grupsVisibles.length} />
              </div>
            )}
            <div className="px-4 py-3 space-y-1 max-h-64 overflow-y-auto">
              {grups.length === 0 && (
                <p className="text-sm text-gray-400 text-center py-4">No hi ha grups creats.</p>
              )}
              {grups.length > 0 && grupsVisibles.length === 0 && (
                <p className="text-sm text-gray-400 text-center py-4">Cap grup coincideix amb els filtres.</p>
              )}
              {grupsVisibles.map(g => (
                <button key={g.id}
                  onClick={() => handleAssign(g.id)}
                  className="w-full text-left px-4 py-3 rounded-lg hover:bg-brand-50 flex items-center justify-between gap-3">
                  <span>
                    <span className="font-medium text-gray-800 block">{g.name}</span>
                    {g.modulNom && <span className="text-xs text-gray-500 block">{g.modulNom}</span>}
                  </span>
                  <span className="text-xs text-gray-400 whitespace-nowrap">{g.students.length} alumnes</span>
                </button>
              ))}
            </div>
            <div className="px-6 py-4 border-t border-gray-100 flex justify-end">
              <button onClick={() => setAssigning(null)}
                className="border border-gray-300 rounded-lg px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                Cancel·lar
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}

import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import Md from '../../components/Md'
import { getCopies, getExam } from '../../api/exams'
import type { CopiesCoincidencia, CopiesInforme, CopiesParella, Exam } from '../../types'

const CODI: CopiesCoincidencia['tipus'][] = ['BASH_SCRIPT', 'PS_SCRIPT', 'JAVA_PROG', 'HTML_CSS']

/** Text amb els fragments coincidents ressaltats. */
function Marcat({ text, marques }: { text: string; marques: [number, number][] }) {
  const trossos: { t: string; marca: boolean }[] = []
  let pos = 0
  for (const [inici, fi] of [...marques].sort((a, b) => a[0] - b[0])) {
    if (inici > pos) trossos.push({ t: text.slice(pos, inici), marca: false })
    trossos.push({ t: text.slice(Math.max(inici, pos), fi), marca: true })
    pos = Math.max(pos, fi)
  }
  if (pos < text.length) trossos.push({ t: text.slice(pos), marca: false })
  return (
    <>
      {trossos.map((tr, i) => tr.marca
        ? <mark key={i} className="bg-amber-200 text-gray-900 rounded-sm">{tr.t}</mark>
        : <span key={i}>{tr.t}</span>)}
    </>
  )
}

export default function CopiesPage() {
  const { examId } = useParams<{ examId: string }>()
  const [exam, setExam] = useState<Exam | null>(null)
  const [informe, setInforme] = useState<CopiesInforme | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!examId) return
    Promise.all([getExam(examId), getCopies(examId)])
      .then(([e, inf]) => { setExam(e); setInforme(inf) })
      .catch(err => setError(err?.response?.data?.error || 'No s\'ha pogut generar l\'informe'))
  }, [examId])

  if (error) return <Layout><p className="text-red-600 text-sm">{error}</p></Layout>
  if (!informe || !exam) return <Layout><p className="text-gray-400 text-sm">Analitzant les respostes…</p></Layout>

  return (
    <Layout>
      <div className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-brand-700">Possibles còpies</h1>
            <p className="text-sm text-gray-600">{exam.title} · {informe.entregats} exàmens entregats</p>
          </div>
          <Link to={`/professor/exams/${examId}/corrections`}
            className="text-sm border border-brand-600 text-brand-600 px-4 py-2 rounded-lg hover:bg-brand-50">
            Correcció
          </Link>
        </div>

        <div className="bg-white border rounded-xl px-5 py-4 text-sm text-gray-700 space-y-1">
          <p>
            Parells d'alumnes amb respostes molt semblants (a partir del <strong>{informe.llindarPercent} %</strong>,
            o del <strong>{informe.llindarApuntsPercent} %</strong> a les preguntes 📖 amb apunts)
            a preguntes de text, scripts, Java o HTML, o amb <strong>{informe.minErradesTest} o més errades de test</strong> en
            què tots dos van triar la mateixa opció incorrecta.
          </p>
          <p className="text-gray-500">
            No es té en compte el que coincideix amb l'enunciat, la resposta correcta o el que escriu bona part de la classe,
            ni les respostes curtes ni les comandes d'una línia. <strong>Una semblança alta no demostra una còpia:</strong> és
            un indici perquè ho revisis.
          </p>
        </div>

        {informe.parelles.length === 0 ? (
          <p className="text-sm text-green-800 bg-green-50 border border-green-200 rounded-xl p-5">
            ✓ No s'ha trobat cap parell de respostes especialment semblants.
          </p>
        ) : (
          <div className="space-y-3">
            <p className="text-sm text-gray-600">{informe.parelles.length} {informe.parelles.length === 1 ? 'parell' : 'parells'} per revisar, dels més sospitosos als menys.</p>
            {informe.parelles.map((p, i) => <TargetaParella key={`${p.sessioA}-${p.sessioB}`} p={p} obertInicial={i === 0} />)}
          </div>
        )}
      </div>
    </Layout>
  )
}

function TargetaParella({ p, obertInicial }: { p: CopiesParella; obertInicial: boolean }) {
  const [obert, setObert] = useState(obertInicial)
  return (
    <section className="bg-white border rounded-xl">
      <button onClick={() => setObert(o => !o)} aria-expanded={obert}
        className="w-full text-left px-5 py-4 flex flex-wrap items-center justify-between gap-3 hover:bg-gray-50 rounded-xl">
        <span className="font-semibold text-gray-800">{p.alumneA} <span className="text-gray-400 font-normal">i</span> {p.alumneB}</span>
        <span className="flex flex-wrap items-center gap-2 text-xs">
          {p.coincidencies.length > 0 && (
            <span className="bg-amber-100 text-amber-900 px-2 py-0.5 rounded">
              {p.coincidencies.length} {p.coincidencies.length === 1 ? 'pregunta semblant' : 'preguntes semblants'} · fins al {p.maxSemblanca} %
            </span>
          )}
          {p.erradesTestComunes.length > 0 && (
            <span className="bg-gray-100 text-gray-700 px-2 py-0.5 rounded">
              {p.erradesTestComunes.length} errades de test iguals (preg. {p.erradesTestComunes.join(', ')})
            </span>
          )}
          <span className="text-brand-600">{obert ? 'Amaga ▲' : 'Compara ▼'}</span>
        </span>
      </button>
      {obert && (
        <div className="px-5 pb-5 space-y-5 border-t pt-4">
          {p.coincidencies.map(c => (
            <div key={c.preguntaId} className="space-y-2">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-2 min-w-0">
                  <span className="bg-brand-100 text-brand-700 text-xs px-2 py-0.5 rounded shrink-0">{c.ordre}</span>
                  <Md className="text-sm text-gray-800 line-clamp-2">{c.enunciat}</Md>
                </div>
                <span className="flex items-center gap-1.5 shrink-0">
                  {c.ambApunts && (
                    <span className="text-xs bg-sky-100 text-sky-800 px-2 py-0.5 rounded"
                      title="Es podien fer servir apunts: una part de la semblança pot venir dels apunts">📖 amb apunts</span>
                  )}
                  <span className="text-xs font-semibold text-amber-900 bg-amber-100 px-2 py-0.5 rounded">{c.semblanca} % semblant</span>
                </span>
              </div>
              <div className="grid md:grid-cols-2 gap-3">
                {[{ nom: p.alumneA, text: c.respostaA, marques: c.marquesA },
                  { nom: p.alumneB, text: c.respostaB, marques: c.marquesB }].map((r, i) => (
                  <div key={i} className="min-w-0">
                    <p className="text-xs text-gray-500 mb-1">{r.nom}</p>
                    <div className={`bg-gray-50 border rounded-lg p-3 text-sm whitespace-pre-wrap break-words max-h-80 overflow-auto ${CODI.includes(c.tipus) ? 'font-mono text-xs' : ''}`}>
                      <Marcat text={r.text} marques={r.marques} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
          {p.coincidencies.length === 0 && (
            <p className="text-sm text-gray-600">
              Cap resposta escrita semblant. A les preguntes de test {p.erradesTestComunes.join(', ')} tots dos van triar la mateixa opció incorrecta.
            </p>
          )}
          <p className="text-xs text-gray-400">En groc, els fragments que coincideixen (sense comptar el que és comú a la classe).</p>
        </div>
      )}
    </section>
  )
}

import type { Answer, Question } from '../types'
import { consellsRecuperacio, llistaPreguntes } from '../utils/consellsRecuperacio'

/**
 * Consells d'estudi per a la recuperació: de tot l'examen si no l'ha aprovat i de cada competència suspesa.
 * No surt res si l'alumne ho ha aprovat tot o si encara hi ha notes pendents de correcció.
 */
export default function ConsellsRecuperacio({ questions, answers }: { questions: Question[]; answers: Answer[] }) {
  const { examen, competencies } = consellsRecuperacio(questions, answers)
  if (!examen && competencies.length === 0) return null

  return (
    <div className="bg-amber-50 border border-amber-200 rounded-xl px-5 py-4 space-y-4">
      <p className="text-xs font-semibold text-amber-800 uppercase tracking-wide">Com preparar la recuperació</p>

      {examen && (
        <div className="space-y-1">
          <p className="text-sm font-semibold text-gray-800">Examen sencer (nota {examen.nota.toFixed(2)} / 10)</p>
          <ul className="list-disc pl-5 text-sm text-gray-700 space-y-1">
            {examen.fallades.length > 0 && (
              <li>Comença per les preguntes on has perdut punts: {llistaPreguntes(examen.fallades)}. Rellegeix la teva resposta, el comentari del professor i esbrina què en faltava.</li>
            )}
            <li>Repassa els apunts i les pràctiques de tot el temari de l'examen i fes-ne un resum propi.</li>
            <li>Practica amb exercicis com els de l'examen, en les mateixes condicions (sense ajuda i amb el temps ajustat).</li>
            <li>Reparteix l'estudi en diverses sessions curtes abans de la data; no ho deixis per l'últim dia.</li>
            <li>Si alguna cosa no t'ha quedat clara, pregunta-ho al professor abans de la recuperació.</li>
          </ul>
        </div>
      )}

      {competencies.map(c => (
        <div key={c.codi} className="space-y-1">
          <p className="text-sm font-semibold text-gray-800">
            Competència {c.codi} <span className="font-normal text-gray-500">(nota {c.nota.toFixed(2)} / 10)</span>
          </p>
          <ul className="list-disc pl-5 text-sm text-gray-700 space-y-1">
            {c.fallades.length > 0 && <li>Preguntes d'aquesta competència on has perdut punts: {llistaPreguntes(c.fallades)}.</li>}
            {c.consells.map(t => <li key={t}>{t.charAt(0).toUpperCase() + t.slice(1)}.</li>)}
            <li>Fes un esquema dels continguts de {c.codi} i comprova que pots explicar-los sense mirar els apunts.</li>
          </ul>
        </div>
      ))}
    </div>
  )
}

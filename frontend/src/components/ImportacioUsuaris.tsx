import { useState } from 'react'
import { importCsv, type ImportacioResultat } from '../api/users'

/** Desa un text com a fitxer CSV (amb BOM i ";" perquè Excel en català l'obri bé). */
function desaCsv(nom: string, files: string[][]) {
  const cel = (v: string) => /[";\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v
  const text = '﻿' + files.map(f => f.map(cel).join(';')).join('\r\n') + '\r\n'
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url; a.download = nom
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

const PLANTILLA = [
  ['nom', 'email', 'contrasenya', 'modul', 'grup'],
  ['Anna Puig', 'anna.puig@centre.cat', '', '0483', '1r DAW'],
  ['Biel Mas', 'biel.mas@centre.cat', '', '0483', '1r DAW'],
]

export default function ImportacioUsuaris({ onImportat }: { onImportat: () => void }) {
  const [important, setImportant] = useState(false)
  const [resultat, setResultat] = useState<ImportacioResultat | null>(null)
  const [error, setError] = useState('')

  const handleFitxer = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''   // permet tornar a triar el mateix fitxer
    if (!file) return
    setImportant(true); setError(''); setResultat(null)
    try {
      setResultat(await importCsv(file))
      onImportat()
    } catch (err: any) {
      setError(err?.response?.data?.error || 'No s\'ha pogut importar el fitxer')
    } finally {
      setImportant(false)
    }
  }

  const r = resultat
  return (
    <div className="bg-white rounded-xl border p-6 space-y-4">
      <div>
        <h2 className="font-semibold">Importar alumnes des d'un CSV</h2>
        <p className="text-sm text-gray-600 mt-1">
          Una fila per alumne. Columnes: <code>nom</code>, <code>email</code> i, opcionals, <code>contrasenya</code>,{' '}
          <code>modul</code> (codi), <code>curs</code> i <code>grup</code>.
        </p>
        <ul className="text-xs text-gray-500 mt-2 space-y-0.5 list-disc pl-5">
          <li>Contrasenya en blanc: se'n genera una i les podràs descarregar en acabar.</li>
          <li>Amb <code>modul</code>, l'alumne queda matriculat (al curs actiu si no poses <code>curs</code>).</li>
          <li>Amb <code>grup</code>, s'afegeix al grup; si el grup no existeix, es crea.</li>
          <li>Si el correu ja té compte, no es modifica: només es matricula o s'afegeix al grup.</li>
          <li>Pots desar-lo des d'Excel com a CSV (separat per punt i coma o per comes).</li>
        </ul>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <label className={`text-sm bg-brand-600 text-white px-4 py-2 rounded-lg cursor-pointer hover:bg-brand-700 ${important ? 'opacity-50 pointer-events-none' : ''}`}>
          {important ? 'Important…' : 'Tria el fitxer CSV…'}
          <input type="file" accept=".csv,text/csv" onChange={handleFitxer} disabled={important} className="sr-only" />
        </label>
        <button type="button" onClick={() => desaCsv('plantilla-alumnes.csv', PLANTILLA)}
          className="text-sm text-brand-600 hover:underline">
          Descarrega una plantilla
        </button>
      </div>

      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}

      {r && (
        <div className="space-y-3" role="status">
          <p className="text-sm text-gray-800">
            <strong>{r.created}</strong> {r.created === 1 ? 'compte nou' : 'comptes nous'}
            {r.skipped > 0 && <> · {r.skipped} ja {r.skipped === 1 ? 'existia' : 'existien'}</>}
            {r.matriculats > 0 && <> · {r.matriculats} {r.matriculats === 1 ? 'matrícula' : 'matrícules'}</>}
            {r.afegitsAGrup > 0 && <> · {r.afegitsAGrup} {r.afegitsAGrup === 1 ? 'afegit' : 'afegits'} a grups</>}
            {r.grupsCreats.length > 0 && <> · grups nous: {r.grupsCreats.join(', ')}</>}
          </p>

          {r.contrasenyes.length > 0 && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 space-y-2">
              <p className="text-sm text-amber-900">
                ⚠ S'han generat <strong>{r.contrasenyes.length}</strong> contrasenyes. <strong>Descarrega-les ara</strong>:
                no es tornaran a mostrar (si se'n perd alguna, caldrà canviar-la).
              </p>
              <button type="button"
                onClick={() => desaCsv('contrasenyes-alumnes.csv',
                  [['nom', 'email', 'contrasenya'], ...r.contrasenyes.map(c => [c.nom, c.email, c.contrasenya])])}
                className="text-sm bg-amber-600 text-white px-4 py-1.5 rounded-lg hover:bg-amber-700">
                Descarrega les contrasenyes (CSV)
              </button>
              <details className="text-sm">
                <summary className="cursor-pointer text-amber-900">Veure-les aquí</summary>
                <table className="mt-2 text-xs">
                  <tbody>
                    {r.contrasenyes.map(c => (
                      <tr key={c.email}>
                        <td className="pr-4 py-0.5">{c.nom}</td>
                        <td className="pr-4 py-0.5 text-gray-600">{c.email}</td>
                        <td className="py-0.5 font-mono">{c.contrasenya}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </details>
            </div>
          )}

          {r.errors.length > 0 && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
              <p className="text-sm text-red-800 font-medium">{r.errors.length} {r.errors.length === 1 ? 'fila no s\'ha importat' : 'files no s\'han importat'}:</p>
              <ul className="text-xs text-red-800 mt-1 space-y-0.5 list-disc pl-5">
                {r.errors.map((e, i) => <li key={i}>{e}</li>)}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import Layout from '../../components/Layout'
import { getEmmagatzematge, type Agrupacio, type Emmagatzematge } from '../../api/emmagatzematge'
import { ampladaBarra, coherenciaDisc, percentUs } from '../../utils/emmagatzematge'
import { formatMida } from '../../utils/fitxers'

const AGRUPACIONS: { id: Agrupacio; text: string; capcalera: string }[] = [
  { id: 'professor', text: 'Per professor', capcalera: 'Professor' },
  { id: 'departament', text: 'Per departament', capcalera: 'Departament' },
  { id: 'cicle', text: 'Per cicle', capcalera: 'Cicle' },
  { id: 'modul', text: 'Per mòdul', capcalera: 'Mòdul' },
  { id: 'examen', text: 'Per examen', capcalera: 'Examen' },
]

export default function EmmagatzematgePage() {
  const [agrupa, setAgrupa] = useState<Agrupacio>('professor')
  const [dades, setDades] = useState<Emmagatzematge | null>(null)
  const [carregant, setCarregant] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    setCarregant(true); setError('')
    getEmmagatzematge(agrupa)
      .then(setDades)
      .catch(() => setError('No s\'ha pogut carregar l\'espai ocupat.'))
      .finally(() => setCarregant(false))
  }, [agrupa])

  const files = dades?.taula.files ?? []
  const maxim = files.reduce((m, f) => Math.max(m, f.total), 0)
  const capcalera = AGRUPACIONS.find(a => a.id === agrupa)!.capcalera
  const coherencia = dades ? coherenciaDisc(dades.disc, dades.taula.total.total) : null
  const us = dades ? percentUs(dades.disc) : null

  return (
    <Layout>
      <div className="max-w-6xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-brand-700">Espai ocupat pels exàmens</h1>
          <p className="text-sm text-gray-500 mt-1">
            Fitxers que els professors adjunten a les preguntes i fitxers que els alumnes lliuren. La base de dades
            no hi desa el contingut, només la referència, així que aquest és l'espai dels fitxers al disc del servidor.
          </p>
        </div>

        {error && <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-2">{error}</p>}

        {dades && (
          <section className="bg-white border border-gray-200 rounded-xl p-5 space-y-3">
            <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Disc del servidor</h2>
            {us !== null && dades.disc.espaiLliure !== null && dades.disc.espaiTotal !== null ? (
              <div>
                <div className="flex justify-between text-sm text-gray-700 mb-1">
                  <span>{formatMida(dades.disc.espaiLliure)} lliures de {formatMida(dades.disc.espaiTotal)}</span>
                  <span className={us >= 90 ? 'text-red-700 font-semibold' : us >= 75 ? 'text-amber-700' : 'text-gray-500'}>{us} % ocupat</span>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden" role="img" aria-label={`${us} % del disc ocupat`}>
                  <div className={`h-full ${us >= 90 ? 'bg-red-500' : us >= 75 ? 'bg-amber-500' : 'bg-brand-500'}`} style={{ width: `${us}%` }} />
                </div>
              </div>
            ) : <p className="text-sm text-gray-400">No es pot saber l'espai lliure del disc.</p>}
            <p className="text-sm text-gray-600">
              Al disc: <strong>{formatMida(dades.disc.midaPreguntesDisc)}</strong> de fitxers de preguntes i{' '}
              <strong>{formatMida(dades.disc.midaLliuramentsDisc)}</strong> de lliuraments d'alumnes.
              Segons la base de dades: <strong>{formatMida(dades.taula.total.total)}</strong>.
            </p>
            {coherencia?.tipus === 'sobra' && (
              <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                Hi ha <strong>{formatMida(coherencia.bytes)}</strong> més al disc del que la base de dades coneix: probablement
                fitxers orfes (d'exàmens o sessions esborrats). No s'esborren sols.
              </p>
            )}
            {coherencia?.tipus === 'falta' && (
              <p className="text-sm text-red-800 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                La base de dades registra <strong>{formatMida(coherencia.bytes)}</strong> més dels que hi ha al disc: s'han perdut
                fitxers. Comprova que <code>/opt/exam-files</code> és el mateix directori i restaura'l d'una còpia si cal.
              </p>
            )}
          </section>
        )}

        <div role="tablist" className="flex flex-wrap gap-1 border-b border-gray-200">
          {AGRUPACIONS.map(a => (
            <button key={a.id} role="tab" aria-selected={agrupa === a.id} onClick={() => setAgrupa(a.id)}
              className={`px-4 py-2 text-sm rounded-t-lg border-b-2 -mb-px ${agrupa === a.id
                ? 'border-brand-600 text-brand-700 font-medium' : 'border-transparent text-gray-500 hover:text-gray-800'}`}>
              {a.text}
            </button>
          ))}
        </div>

        <div className="bg-white border border-gray-200 rounded-xl overflow-x-auto" aria-busy={carregant}>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200 text-gray-600">
              <tr>
                <th className="px-4 py-3 text-left font-medium">{capcalera}</th>
                <th className="px-3 py-3 text-right font-medium">Exàmens</th>
                <th className="px-3 py-3 text-right font-medium">Fitxers de preguntes</th>
                <th className="px-3 py-3 text-right font-medium">Lliuraments</th>
                <th className="px-4 py-3 text-right font-medium">Total</th>
                <th className="px-4 py-3 w-40"><span className="sr-only">Proporció</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {!carregant && files.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-6 text-center text-gray-400">Encara no hi ha exàmens.</td></tr>
              )}
              {files.map(f => (
                <tr key={f.clau} className="hover:bg-gray-50">
                  <td className="px-4 py-2.5">
                    <span className="font-medium text-gray-800">{f.nom}</span>
                    {f.detall && <span className="block text-xs text-gray-400">{f.detall}</span>}
                  </td>
                  <td className="px-3 py-2.5 text-right text-gray-600">{f.examens}</td>
                  <td className="px-3 py-2.5 text-right text-gray-600">
                    {formatMida(f.midaPregunta)} <span className="text-xs text-gray-400">({f.fitxersPregunta})</span>
                  </td>
                  <td className="px-3 py-2.5 text-right text-gray-600">
                    {formatMida(f.midaLliuraments)} <span className="text-xs text-gray-400">({f.lliuraments})</span>
                  </td>
                  <td className="px-4 py-2.5 text-right font-semibold text-gray-800">{formatMida(f.total)}</td>
                  <td className="px-4 py-2.5">
                    <div className="h-2 bg-gray-100 rounded-full overflow-hidden" aria-hidden>
                      <div className="h-full bg-brand-500" style={{ width: `${ampladaBarra(f.total, maxim)}%` }} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
            {dades && files.length > 0 && (
              <tfoot className="bg-gray-50 border-t border-gray-200 font-semibold text-gray-800">
                <tr>
                  <td className="px-4 py-3">Total</td>
                  <td className="px-3 py-3 text-right">{dades.taula.total.examens}</td>
                  <td className="px-3 py-3 text-right">
                    {formatMida(dades.taula.total.midaPregunta)} <span className="text-xs font-normal text-gray-400">({dades.taula.total.fitxersPregunta})</span>
                  </td>
                  <td className="px-3 py-3 text-right">
                    {formatMida(dades.taula.total.midaLliuraments)} <span className="text-xs font-normal text-gray-400">({dades.taula.total.lliuraments})</span>
                  </td>
                  <td className="px-4 py-3 text-right">{formatMida(dades.taula.total.total)}</td>
                  <td />
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </Layout>
  )
}

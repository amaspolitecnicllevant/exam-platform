import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { aplicaRevisioIa, previsualitzaRevisioIa, type FilaRevisio, type Previsualitzacio } from '../api/revisioIa'

const fmt = (n: number | null | undefined) => n == null ? '—' : Number(n.toFixed(2)).toString().replace('.', ',')
const ORIGEN = { REVISADA: 'revisada', PROPOSTA: 'proposta', CAP: 'sense nota' } as const

/**
 * Importa la revisió que una IA ha fet de les respostes exportades. Primer es veu què canviaria
 * (res es desa), i després s'apliquen només les files que el professor marca.
 */
export default function ImportarRevisioIa({ examId, onAplicat }: {
  examId: string
  /** Es crida en acabar d'aplicar, amb un missatge per mostrar; la pàgina hauria de recarregar les dades. */
  onAplicat: (missatge: string) => void
}) {
  const [obert, setObert] = useState(false)
  const [text, setText] = useState('')
  const [nomFitxer, setNomFitxer] = useState('')
  const [previ, setPrevi] = useState<Previsualitzacio | null>(null)
  const [marcades, setMarcades] = useState<Set<string>>(new Set())
  const [ocupat, setOcupat] = useState(false)
  const [error, setError] = useState('')

  const tanca = () => { setObert(false); setText(''); setNomFitxer(''); setPrevi(null); setMarcades(new Set()); setError('') }

  useEffect(() => {
    if (!obert) return
    const tecla = (e: KeyboardEvent) => { if (e.key === 'Escape' && !ocupat) tanca() }
    document.addEventListener('keydown', tecla)
    return () => document.removeEventListener('keydown', tecla)
  }, [obert, ocupat])

  const canvis = useMemo(() => previ?.files.filter(f => f.estat === 'CANVI') ?? [], [previ])
  const noAplicables = useMemo(() => previ?.files.filter(f => f.estat !== 'CANVI') ?? [], [previ])
  const sobreescrites = canvis.filter(f => f.answerId && marcades.has(f.answerId) && f.sobreescriuRevisada).length

  const llegeixFitxer = async (f: File | undefined) => {
    if (!f) return
    setError('')
    if (f.size > 2_000_000) { setError('El fitxer és massa gran (màxim 2 MB).'); return }
    setText(await f.text())
    setNomFitxer(f.name)
  }

  const missatge = (err: any, perDefecte: string) => err?.response?.data?.error || perDefecte

  const previsualitza = async () => {
    setOcupat(true); setError('')
    try {
      const p = await previsualitzaRevisioIa(examId, text)
      setPrevi(p)
      // Per defecte s'accepta tot menys el que substituiria una nota que ja havies revisat
      setMarcades(new Set(p.files.filter(f => f.estat === 'CANVI' && f.answerId && !f.sobreescriuRevisada).map(f => f.answerId!)))
    } catch (err) {
      setError(missatge(err, 'No s\'ha pogut llegir el fitxer.'))
    } finally {
      setOcupat(false)
    }
  }

  const aplica = async () => {
    if (!previ || marcades.size === 0) return
    if (sobreescrites > 0 && !confirm(`${sobreescrites} d'aquestes notes substitueixen una nota que ja havies revisat. Vols continuar?`)) return
    if (previ.notesPublicades && !confirm('Les notes ja són visibles per als alumnes: aquests canvis els afectaran. Vols continuar?')) return
    setOcupat(true); setError('')
    try {
      const acceptades = canvis.filter(f => f.answerId && marcades.has(f.answerId))
        .map(f => ({ answerId: f.answerId!, notaActual: f.notaActual }))
      const r = await aplicaRevisioIa(examId, text, acceptades)
      const salt = r.saltades > 0 ? ` (${r.saltades} saltades: ${r.motius.join('; ') || 'ja no eren vàlides'})` : ''
      onAplicat(`Revisió amb IA aplicada: ${r.aplicades} ${r.aplicades === 1 ? 'nota canviada' : 'notes canviades'}${salt}.`)
      tanca()
    } catch (err) {
      setError(missatge(err, 'No s\'han pogut aplicar els canvis.'))
    } finally {
      setOcupat(false)
    }
  }

  const alterna = (id: string) => setMarcades(prev => {
    const n = new Set(prev)
    if (n.has(id)) n.delete(id); else n.add(id)
    return n
  })

  const fila = (f: FilaRevisio) => {
    const marcada = !!f.answerId && marcades.has(f.answerId)
    const delta = f.notaNova != null && f.notaActual != null ? f.notaNova - f.notaActual : null
    return (
      <tr key={f.linia} className={`border-t border-gray-100 ${f.sobreescriuRevisada ? 'bg-amber-50/60' : ''}`}>
        <td className="px-2 py-1.5 align-top">
          <input type="checkbox" checked={marcada} onChange={() => f.answerId && alterna(f.answerId)}
            aria-label={`Aplicar el canvi de ${f.alumne}, pregunta ${f.pregunta}`} className="accent-brand-600" />
        </td>
        <td className="px-2 py-1.5 align-top text-xs text-gray-800">{f.alumne}</td>
        <td className="px-2 py-1.5 align-top text-xs text-gray-700" title={f.enunciat ?? undefined}>P{f.pregunta}</td>
        <td className="px-2 py-1.5 align-top text-xs text-gray-600 whitespace-nowrap">
          {fmt(f.notaActual)} / {fmt(f.puntsMax)}
          <span className={`block ${f.origenActual === 'REVISADA' ? 'text-amber-700 font-medium' : 'text-gray-400'}`}>
            {f.origenActual ? ORIGEN[f.origenActual] : ''}
          </span>
        </td>
        <td className="px-2 py-1.5 align-top text-xs font-semibold text-brand-700 whitespace-nowrap">
          → {fmt(f.notaNova)}
          {delta != null && delta !== 0 && (
            <span className={`ml-1 font-normal ${delta > 0 ? 'text-green-700' : 'text-red-700'}`}>({delta > 0 ? '+' : ''}{fmt(delta)})</span>
          )}
        </td>
        <td className="px-2 py-1.5 align-top text-xs text-gray-600">
          {f.justificacio}
          {f.motiu && <span className="block text-gray-400 italic">{f.motiu}</span>}
        </td>
      </tr>
    )
  }

  return (
    <>
      <button type="button" onClick={() => setObert(true)}
        title="Importa la revisió que una IA ha fet de les respostes exportades"
        className="text-xs border border-violet-300 text-violet-700 px-3 py-1.5 rounded-lg hover:bg-violet-50">
        Importar revisió IA
      </button>
      {obert && createPortal(
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div role="dialog" aria-modal="true" aria-label="Importar revisió feta per una IA"
            className="bg-white rounded-2xl shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col">
            <div className="px-5 py-3 border-b flex items-center justify-between">
              <h2 className="font-semibold text-gray-800">Importar revisió feta per una IA</h2>
              <button onClick={tanca} disabled={ocupat} aria-label="Tancar" className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
            </div>

            <div className="p-5 overflow-y-auto space-y-4 text-sm">
              {!previ && (
                <>
                  <p className="text-gray-600 text-xs">
                    Exporta «Respostes anònimes (.md)» des de <strong>Exportar</strong>, passa-les a la IA i enganxa aquí la seva resposta
                    (el bloc CSV), o puja el fitxer. Abans d'aplicar res veuràs tots els canvis.
                  </p>
                  <label className="block">
                    <span className="text-xs font-medium text-gray-700">Fitxer (.csv, .md o .txt)</span>
                    <input type="file" accept=".csv,.md,.txt,text/*" onChange={e => llegeixFitxer(e.target.files?.[0])}
                      className="block mt-1 text-xs" />
                    {nomFitxer && <span className="text-xs text-gray-500">Llegit: {nomFitxer}</span>}
                  </label>
                  <label className="block">
                    <span className="text-xs font-medium text-gray-700">…o enganxa la resposta de la IA</span>
                    <textarea value={text} onChange={e => { setText(e.target.value); setNomFitxer('') }} rows={10}
                      placeholder={'alumne;pregunta;nota;justificacio\nAlumne 7F3A2C;1;1.5;Explica bé el concepte…'}
                      className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-xs font-mono resize-y focus:outline-none focus:ring-2 focus:ring-brand-500" />
                  </label>
                </>
              )}

              {previ && (
                <>
                  <div className="flex flex-wrap gap-2 text-xs">
                    <span className="px-2 py-1 rounded bg-green-50 text-green-800 border border-green-200">{previ.canvis} canvis</span>
                    <span className="px-2 py-1 rounded bg-gray-50 text-gray-700 border border-gray-200">{previ.iguals} sense canvi</span>
                    <span className="px-2 py-1 rounded bg-gray-50 text-gray-700 border border-gray-200">{previ.ignorades} ignorades</span>
                    <span className={`px-2 py-1 rounded border ${previ.errors ? 'bg-red-50 text-red-800 border-red-200' : 'bg-gray-50 text-gray-700 border-gray-200'}`}>{previ.errors} errors</span>
                  </div>
                  {previ.avisos.map((a, i) => <p key={i} role="alert" className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2">{a}</p>)}
                  {previ.notesPublicades && (
                    <p role="alert" className="text-xs text-red-800 bg-red-50 border border-red-200 rounded px-3 py-2">
                      Les notes d'aquest examen ja són visibles per als alumnes: si apliques canvis, els veuran.
                    </p>
                  )}

                  {canvis.length > 0 && (
                    <div className="border rounded-lg overflow-x-auto">
                      <table className="w-full text-left">
                        <thead className="bg-gray-50 text-xs text-gray-500">
                          <tr>
                            <th className="px-2 py-1.5 w-8">
                              <input type="checkbox" aria-label="Marcar o desmarcar tots els canvis" className="accent-brand-600"
                                checked={canvis.every(f => f.answerId && marcades.has(f.answerId))}
                                onChange={e => setMarcades(e.target.checked ? new Set(canvis.map(f => f.answerId!)) : new Set())} />
                            </th>
                            <th className="px-2 py-1.5 font-medium">Alumne</th>
                            <th className="px-2 py-1.5 font-medium">Preg.</th>
                            <th className="px-2 py-1.5 font-medium">Ara</th>
                            <th className="px-2 py-1.5 font-medium">Nova</th>
                            <th className="px-2 py-1.5 font-medium">Justificació de la IA</th>
                          </tr>
                        </thead>
                        <tbody>{canvis.map(fila)}</tbody>
                      </table>
                    </div>
                  )}
                  {canvis.some(f => f.sobreescriuRevisada) && (
                    <p className="text-xs text-amber-800">Les files en groc substitueixen una nota que ja havies revisat: venen desmarcades.</p>
                  )}

                  {previ.alumnes.length > 0 && (
                    <details className="text-xs">
                      <summary className="cursor-pointer text-gray-700 font-medium">Efecte a la nota final de cada alumne (si s'apliquessin tots els canvis)</summary>
                      <table className="mt-2 w-full text-left">
                        <tbody>
                          {previ.alumnes.map(a => (
                            <tr key={a.codi} className="border-t border-gray-100">
                              <td className="py-1 pr-3 text-gray-800">{a.nom}</td>
                              <td className="py-1 text-gray-600">{fmt(a.notaAbans)} → <strong>{fmt(a.notaDespres)}</strong></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </details>
                  )}

                  {noAplicables.length > 0 && (
                    <details className="text-xs">
                      <summary className="cursor-pointer text-gray-700 font-medium">Files que no s'apliquen ({noAplicables.length})</summary>
                      <ul className="mt-2 space-y-1">
                        {noAplicables.map(f => (
                          <li key={f.linia} className={f.estat === 'ERROR' ? 'text-red-700' : 'text-gray-500'}>
                            Línia {f.linia}: {f.alumne}{f.pregunta != null ? ` · P${f.pregunta}` : ''} — {f.motiu}
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                </>
              )}

              {error && <p role="alert" className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>}
            </div>

            <div className="px-5 py-3 border-t flex items-center justify-end gap-2">
              {previ && <button onClick={() => { setPrevi(null); setError('') }} disabled={ocupat}
                className="text-sm border border-gray-300 text-gray-700 px-4 py-1.5 rounded-lg hover:bg-gray-50 mr-auto">Enrere</button>}
              <button onClick={tanca} disabled={ocupat}
                className="text-sm border border-gray-300 text-gray-700 px-4 py-1.5 rounded-lg hover:bg-gray-50">Cancel·la</button>
              {!previ ? (
                <button onClick={previsualitza} disabled={ocupat || !text.trim()}
                  className="text-sm bg-brand-600 text-white px-4 py-1.5 rounded-lg hover:bg-brand-700 disabled:opacity-50">
                  {ocupat ? 'Llegint…' : 'Previsualitza els canvis'}
                </button>
              ) : (
                <button onClick={aplica} disabled={ocupat || marcades.size === 0}
                  className="text-sm bg-green-600 text-white px-4 py-1.5 rounded-lg hover:bg-green-700 disabled:opacity-50">
                  {ocupat ? 'Aplicant…' : `Aplicar ${marcades.size} ${marcades.size === 1 ? 'canvi' : 'canvis'}`}
                </button>
              )}
            </div>
          </div>
        </div>,
        document.body
      )}
    </>
  )
}

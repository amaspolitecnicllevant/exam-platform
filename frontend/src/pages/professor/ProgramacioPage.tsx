import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Layout from '../../components/Layout'
import { getModuls } from '../../api/moduls'
import { getProgramacio, desaProgramacio, importaProgramacioCsv, getResultatsModul } from '../../api/programacio'
import { ResultatsUnitats, ResultatsCompetencies } from '../../components/ResultatsProgramacio'
import { useAuth } from '../../context/AuthContext'
import type { Modul, Programacio, ResultatsModul } from '../../types'

interface FilaCo { codi: string; descripcio: string }
interface FilaUt { codi: string; nom: string; competencies: string[] }

const campCls = 'border border-gray-300 rounded px-2 py-1 text-sm text-gray-900 focus:outline-none focus:ring-1 focus:ring-brand-400'

const desdeProgramacio = (p: Programacio): { cos: FilaCo[]; uts: FilaUt[] } => ({
  cos: p.competencies.map(c => ({ codi: c.codi, descripcio: c.descripcio })),
  uts: p.unitats.map(u => ({ codi: u.codi, nom: u.nom, competencies: [...u.competencies] })),
})

/** Competències (CO) i unitats de treball (UT) d'un mòdul, i quines UT treballen cada competència. */
export default function ProgramacioPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const [moduls, setModuls] = useState<Modul[]>([])
  const [modulId, setModulId] = useState(params.get('modul') ?? '')
  const [cos, setCos] = useState<FilaCo[]>([])
  const [uts, setUts] = useState<FilaUt[]>([])
  const [canviat, setCanviat] = useState(false)
  const [error, setError] = useState('')
  const [missatge, setMissatge] = useState('')
  const [ocupat, setOcupat] = useState(false)
  const fitxerRef = useRef<HTMLInputElement>(null)
  // Notes mitjanes per UT i competència; es tornen a carregar quan es desa o s'importa la programació
  const [resultats, setResultats] = useState<ResultatsModul | null>(null)
  const [versio, setVersio] = useState(0)

  useEffect(() => {
    getModuls({ meus: true }).then(ms => {
      setModuls(ms)
      if (!modulId && ms.length > 0) setModulId(ms[0].id)
    }).catch(() => setError('No s\'han pogut carregar els mòduls'))
  }, [])   // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!modulId) return
    setError(''); setMissatge(''); setCanviat(false)
    getProgramacio(modulId).then(p => { const d = desdeProgramacio(p); setCos(d.cos); setUts(d.uts) })
      .catch(e => { setCos([]); setUts([]); setError(e?.response?.data?.error || 'No s\'ha pogut carregar la programació') })
  }, [modulId])

  useEffect(() => {
    if (!modulId) { setResultats(null); return }
    getResultatsModul(modulId).then(setResultats).catch(() => setResultats(null))
  }, [modulId, versio])

  const trieModul = (id: string) => {
    if (canviat && !confirm('Hi ha canvis sense desar. Els vols descartar?')) return
    setModulId(id)
    setParams(id ? { modul: id } : {})
  }

  const marca = () => { setCanviat(true); setMissatge('') }

  const canviaCo = (i: number, camp: keyof FilaCo, valor: string) => {
    const antic = cos[i].codi
    const nou = camp === 'codi' ? valor.toUpperCase() : valor
    setCos(cos.map((c, j) => j === i ? { ...c, [camp]: nou } : c))
    // Si canvia el codi, les UT que la feien servir la segueixen fent servir
    if (camp === 'codi' && antic !== nou) {
      setUts(uts.map(u => ({ ...u, competencies: u.competencies.map(c => c === antic ? nou : c) })))
    }
    marca()
  }

  const treuCo = (i: number) => {
    const codi = cos[i].codi
    setCos(cos.filter((_, j) => j !== i))
    setUts(uts.map(u => ({ ...u, competencies: u.competencies.filter(c => c !== codi) })))
    marca()
  }

  const afegeixCo = () => { setCos([...cos, { codi: `CO${cos.length + 1}`, descripcio: '' }]); marca() }

  const canviaUt = (i: number, camp: 'codi' | 'nom', valor: string) => {
    setUts(uts.map((u, j) => j === i ? { ...u, [camp]: camp === 'codi' ? valor.toUpperCase() : valor } : u)); marca()
  }
  const treuUt = (i: number) => { setUts(uts.filter((_, j) => j !== i)); marca() }
  const afegeixUt = () => {
    setUts([...uts, { codi: `UT${String(uts.length + 1).padStart(2, '0')}`, nom: '', competencies: [] }]); marca()
  }
  const alterna = (i: number, codi: string) => {
    setUts(uts.map((u, j) => j !== i ? u : {
      ...u,
      competencies: u.competencies.includes(codi) ? u.competencies.filter(c => c !== codi) : [...u.competencies, codi],
    }))
    marca()
  }

  const desa = async () => {
    setOcupat(true); setError(''); setMissatge('')
    try {
      const p = await desaProgramacio(modulId, { competencies: cos, unitats: uts })
      const d = desdeProgramacio(p); setCos(d.cos); setUts(d.uts)
      setCanviat(false); setMissatge('Programació desada'); setVersio(v => v + 1)
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'ha pogut desar la programació')
    } finally { setOcupat(false) }
  }

  const importa = async (fitxer: File) => {
    if ((cos.length > 0 || uts.length > 0) &&
        !confirm('Importar el CSV substitueix tota la programació actual d\'aquest mòdul. Continuar?')) return
    setOcupat(true); setError(''); setMissatge('')
    try {
      const p = await importaProgramacioCsv(modulId, fitxer)
      const d = desdeProgramacio(p); setCos(d.cos); setUts(d.uts)
      setCanviat(false); setVersio(v => v + 1); setMissatge(`CSV importat: ${p.competencies.length} competències i ${p.unitats.length} unitats`)
    } catch (e: any) {
      setError(e?.response?.data?.error || 'No s\'ha pogut importar el CSV')
    } finally {
      setOcupat(false)
      if (fitxerRef.current) fitxerRef.current.value = ''
    }
  }

  return (
    <Layout>
      <div className="space-y-5 max-w-5xl">
        <div>
          <h1 className="text-2xl font-bold text-brand-700">Programació del mòdul</h1>
          <p className="text-sm text-gray-500 mt-1">
            L'avaluació és per competències. Defineix les competències del mòdul i marca quines treballa cada unitat de treball:
            als exàmens, cada pregunta indicarà la competència que treballa.
          </p>
        </div>

        <label className="block">
          <span className="block text-xs font-medium text-gray-600 mb-1">Mòdul</span>
          <select value={modulId} onChange={e => trieModul(e.target.value)} className={`${campCls} min-w-[22rem]`}>
            {moduls.length === 0 && <option value="">{user?.role === 'ADMIN' ? 'No hi ha cap mòdul' : 'No tens cap mòdul assignat'}</option>}
            {moduls.map(m => <option key={m.id} value={m.id}>{m.codi} · {m.nom} ({m.cicleNom})</option>)}
          </select>
        </label>

        {error && <p role="alert" className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>}
        {missatge && <p role="status" className="text-sm text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2">{missatge}</p>}

        {modulId && (
          <>
            <section className="bg-white border border-gray-200 rounded-xl p-4 space-y-3">
              <h2 className="font-semibold text-gray-800">Competències</h2>
              {cos.length === 0 && <p className="text-sm text-gray-400">Encara no n'hi ha cap.</p>}
              {cos.map((c, i) => (
                <div key={i} className="flex gap-2 items-center">
                  <input value={c.codi} onChange={e => canviaCo(i, 'codi', e.target.value)} aria-label="Codi de la competència"
                    className={`${campCls} w-24 font-mono`} maxLength={20} />
                  <input value={c.descripcio} onChange={e => canviaCo(i, 'descripcio', e.target.value)}
                    aria-label="Descripció de la competència" placeholder="Descripció" className={`${campCls} flex-1`} />
                  <button onClick={() => treuCo(i)} title="Treure la competència"
                    className="text-gray-400 hover:text-red-600 px-2">×</button>
                </div>
              ))}
              <button onClick={afegeixCo} className="text-sm text-brand-600 hover:underline">+ Afegir competència</button>
            </section>

            {resultats && <ResultatsCompetencies resultats={resultats} />}

            <section className="bg-white border border-gray-200 rounded-xl p-4 space-y-3">
              <h2 className="font-semibold text-gray-800">Unitats de treball i competències que treballen</h2>
              {uts.length === 0 && <p className="text-sm text-gray-400">Encara no n'hi ha cap.</p>}
              {uts.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="text-sm border-collapse">
                    <thead>
                      <tr>
                        <th className="text-left font-medium text-gray-500 px-2 pb-1">UT</th>
                        <th className="text-left font-medium text-gray-500 px-2 pb-1">Nom</th>
                        {cos.map(c => (
                          <th key={c.codi} title={c.descripcio || undefined}
                            className="font-medium text-indigo-700 px-2 pb-1 font-mono">{c.codi}</th>
                        ))}
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {uts.map((u, i) => (
                        <tr key={i} className="border-t border-gray-100">
                          <td className="px-2 py-1">
                            <input value={u.codi} onChange={e => canviaUt(i, 'codi', e.target.value)} aria-label="Codi de la unitat"
                              className={`${campCls} w-24 font-mono`} maxLength={20} />
                          </td>
                          <td className="px-2 py-1">
                            <input value={u.nom} onChange={e => canviaUt(i, 'nom', e.target.value)} aria-label="Nom de la unitat"
                              placeholder="Nom" className={`${campCls} w-64`} />
                          </td>
                          {cos.map(c => (
                            <td key={c.codi} className="text-center px-2">
                              <input type="checkbox" checked={u.competencies.includes(c.codi)} onChange={() => alterna(i, c.codi)}
                                aria-label={`${u.codi} treballa ${c.codi}`} className="accent-brand-600 w-4 h-4" />
                            </td>
                          ))}
                          <td className="px-2">
                            <button onClick={() => treuUt(i)} title="Treure la unitat"
                              className="text-gray-400 hover:text-red-600">×</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <button onClick={afegeixUt} className="text-sm text-brand-600 hover:underline">+ Afegir unitat</button>
            </section>

            {resultats && <ResultatsUnitats resultats={resultats} />}

            <div className="flex items-center gap-4 flex-wrap">
              <button onClick={desa} disabled={!canviat || ocupat}
                className="bg-brand-600 text-white text-sm px-4 py-2 rounded hover:bg-brand-700 disabled:opacity-40">
                {ocupat ? 'Desant…' : 'Desar la programació'}
              </button>
              {canviat && <span className="text-xs text-amber-600">Hi ha canvis sense desar</span>}
              <label className="text-sm text-brand-600 hover:underline cursor-pointer ml-auto">
                Importar d'un CSV…
                <input ref={fitxerRef} type="file" accept=".csv,text/csv,text/plain" className="hidden"
                  onChange={e => { const f = e.target.files?.[0]; if (f) importa(f) }} />
              </label>
            </div>

            <details className="text-sm text-gray-600">
              <summary className="cursor-pointer text-gray-500">Format del CSV</summary>
              <div className="mt-2 space-y-2">
                <p>
                  Una fila per cada parella (unitat, competència). Separador «;», «,» o tabulador; la fila de capçalera és opcional;
                  la descripció de la competència i el nom de la unitat són opcionals. <strong>Substitueix tota la programació del mòdul</strong>,
                  però no deixa treure competències que ja tenen preguntes ni unitats que avalua algun examen.
                </p>
                <pre className="bg-gray-900 text-green-300 rounded p-3 text-xs overflow-auto">{`unitat;competència;descripció;nom de la unitat
UT01;CO1;Instal·la i configura sistemes operatius;Instal·lació
UT01;CO2;Gestiona usuaris i permisos;Instal·lació
UT02;CO2;Gestiona usuaris i permisos;Usuaris i grups`}</pre>
              </div>
            </details>
          </>
        )}
      </div>
    </Layout>
  )
}

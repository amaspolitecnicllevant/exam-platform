import { useEffect, useState, useRef, ChangeEvent } from 'react'
import Layout from '../../components/Layout'
import { getConfiguracio, updateConfiguracio, uploadLogo, deleteLogo, getLogoUrl, getCopiesSeguretat, type ConfiguracioDto, type CopiesSeguretat } from '../../api/configuracio'
import { useConfiguracio } from '../../context/ConfiguracioContext'

export default function ConfiguracioPage() {
  const { reload } = useConfiguracio()
  const [cfg, setCfg] = useState<ConfiguracioDto | null>(null)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState('')
  const [error, setError] = useState('')
  const fileRef = useRef<HTMLInputElement>(null)

  const [nomCentre, setNomCentre]               = useState('')
  const [colorMarca, setColorMarca]             = useState('#b0306a')
  const [cursActiu, setCursActiu]               = useState('')
  const [duradaDefecte, setDuradaDefecte]       = useState(90)
  const [penalitzacio, setPenalitzacio]         = useState('0.25')
  const [focusLoss, setFocusLoss]               = useState(5)
  const [copiesLlindar, setCopiesLlindar]       = useState(80)
  const [copiesLlindarApunts, setCopiesLlindarApunts] = useState(95)
  const [gracePeriod, setGracePeriod]           = useState(30)
  const [dominisOauth, setDominisOauth]         = useState('')

  useEffect(() => {
    getConfiguracio().then(c => {
      setCfg(c)
      setNomCentre(c.nomCentre ?? '')
      setColorMarca(c.colorMarca ?? '#b0306a')
      setCursActiu(c.cursActiu ?? '')
      setDuradaDefecte(c.duradaDefecte ?? 90)
      setPenalitzacio(String(c.penalitzacioDefecte ?? 0.25))
      setFocusLoss(c.focusLossThreshold ?? 5)
      setCopiesLlindar(c.copiesLlindar ?? 80)
      setCopiesLlindarApunts(c.copiesLlindarApunts ?? 95)
      setGracePeriod(c.gracePeriodSeconds ?? 30)
      setDominisOauth(c.dominisOauth ?? '')
    })
  }, [])

  const handleSave = async () => {
    setSaving(true); setMsg(''); setError('')
    try {
      await updateConfiguracio({
        nomCentre,
        colorMarca,
        cursActiu,
        duradaDefecte,
        penalitzacioDefecte: parseFloat(penalitzacio),
        focusLossThreshold: focusLoss,
        copiesLlindar,
        copiesLlindarApunts,
        gracePeriodSeconds: gracePeriod,
        dominisOauth,
      })
      reload()
      setMsg('Configuració desada correctament.')
    } catch {
      setError('Error en desar la configuració.')
    } finally {
      setSaving(false)
    }
  }

  const handleLogoChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setError(''); setMsg('')
    try {
      await uploadLogo(file)
      setCfg(prev => prev ? { ...prev, hasLogo: true } : prev)
      reload()
      setMsg('Logo pujat correctament.')
    } catch {
      setError('Error pujant el logo (màx 500 KB, format imatge).')
    }
    if (fileRef.current) fileRef.current.value = ''
  }

  const handleDeleteLogo = async () => {
    if (!confirm('Eliminar el logo?')) return
    setError(''); setMsg('')
    try {
      await deleteLogo()
      setCfg(prev => prev ? { ...prev, hasLogo: false } : prev)
      reload()
      setMsg('Logo eliminat.')
    } catch {
      setError('Error eliminant el logo.')
    }
  }

  if (!cfg) return <Layout><p className="text-sm text-gray-500">Carregant...</p></Layout>

  return (
    <Layout>
      <div className="max-w-2xl">
        <h1 className="text-2xl font-bold text-brand-700 mb-6">Configuració del sistema</h1>

        {msg   && <p className="mb-4 text-sm text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2">{msg}</p>}
        {error && <p className="mb-4 text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2">{error}</p>}

        {/* Identitat */}
        <section className="bg-white rounded-xl border border-gray-200 p-6 mb-6 space-y-5">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Identitat del centre</h2>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Nom del centre</label>
            <input value={nomCentre} onChange={e => setNomCentre(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Color de marca</label>
            <div className="flex items-center gap-3">
              <input type="color" value={colorMarca} onChange={e => setColorMarca(e.target.value)}
                className="h-9 w-16 rounded border border-gray-300 cursor-pointer p-0.5" />
              <input value={colorMarca} onChange={e => setColorMarca(e.target.value)}
                className="w-32 border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <div className="h-9 w-20 rounded-lg border border-gray-200" style={{ backgroundColor: colorMarca }} />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Logo (imatge, màx 500 KB)</label>
            <div className="flex items-center gap-4">
              {cfg.hasLogo && (
                <img src={getLogoUrl()} alt="Logo" className="h-12 w-auto rounded border border-gray-200 object-contain" />
              )}
              <div className="flex gap-2">
                <button onClick={() => fileRef.current?.click()}
                  className="text-sm bg-brand-600 hover:bg-brand-700 text-white rounded-lg px-3 py-1.5">
                  {cfg.hasLogo ? 'Canviar logo' : 'Pujar logo'}
                </button>
                {cfg.hasLogo && (
                  <button onClick={handleDeleteLogo}
                    className="text-sm border border-red-300 text-red-600 hover:bg-red-50 rounded-lg px-3 py-1.5">
                    Eliminar
                  </button>
                )}
              </div>
            </div>
            <input ref={fileRef} type="file" accept="image/*" onChange={handleLogoChange} className="hidden" />
          </div>
        </section>

        {/* Curs acadèmic */}
        <section className="bg-white rounded-xl border border-gray-200 p-6 mb-6 space-y-5">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Curs acadèmic</h2>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Curs actiu (ex: 2025-26)</label>
            <input value={cursActiu} onChange={e => setCursActiu(e.target.value)}
              placeholder="2025-26"
              className="w-48 border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
          </div>
        </section>

        {/* Configuració d'exàmens */}
        <section className="bg-white rounded-xl border border-gray-200 p-6 mb-6 space-y-5">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Valors per defecte d'exàmens</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Durada per defecte (min)</label>
              <input type="number" min={10} max={360} value={duradaDefecte}
                onChange={e => setDuradaDefecte(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Penalització per defecte</label>
              <input type="number" step="0.05" min={0} max={1} value={penalitzacio}
                onChange={e => setPenalitzacio(e.target.value)}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <p className="text-xs text-gray-400 mt-1">Fracció del valor de la pregunta (0 = sense penalització)</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Llindar focus perdut (cops)</label>
              <input type="number" min={1} max={20} value={focusLoss}
                onChange={e => setFocusLoss(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <p className="text-xs text-gray-400 mt-1">A partir d'aquest nombre, l'alumne es marca al monitor</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Llindar de semblança per a còpies (%)</label>
              <input type="number" min={50} max={100} value={copiesLlindar}
                onChange={e => setCopiesLlindar(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <p className="text-xs text-gray-400 mt-1">Semblança mínima perquè l'informe de possibles còpies marqui una pregunta (50–100)</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Llindar de còpies a preguntes amb apunts (%)</label>
              <input type="number" min={50} max={100} value={copiesLlindarApunts}
                onChange={e => setCopiesLlindarApunts(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <p className="text-xs text-gray-400 mt-1">Més alt que l'anterior: dos alumnes poden haver copiat el mateix dels apunts (50–100)</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Període de gràcia (s)</label>
              <input type="number" min={0} max={300} value={gracePeriod}
                onChange={e => setGracePeriod(Number(e.target.value))}
                className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500 focus:outline-none" />
              <p className="text-xs text-gray-400 mt-1">Temps extra al finalitzar el temps</p>
            </div>
          </div>
        </section>

        {/* Còpies de seguretat */}
        <EstatCopies />

        {/* OAuth */}
        <section className="bg-white rounded-xl border border-gray-200 p-6 mb-6 space-y-5">
          <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Autenticació OAuth2</h2>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Dominis permesos (separats per comes)</label>
            <input value={dominisOauth} onChange={e => setDominisOauth(e.target.value)}
              placeholder="politecnicllevant.cat, altre.edu"
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono focus:ring-2 focus:ring-brand-500 focus:outline-none" />
            <p className="text-xs text-gray-400 mt-1">Deixa buit per usar el valor de configuració ({import.meta.env.VITE_DEFAULT_OAUTH_DOMAIN ?? 'fitxer .env'})</p>
          </div>
        </section>

        <button onClick={handleSave} disabled={saving}
          className="bg-brand-600 hover:bg-brand-700 text-white rounded-lg px-6 py-2.5 text-sm font-medium disabled:opacity-50">
          {saving ? 'Desant...' : 'Desar canvis'}
        </button>
      </div>
    </Layout>
  )
}

/** Estat de l'última còpia de seguretat i com restaurar-ne una. */
function EstatCopies() {
  const [estat, setEstat] = useState<CopiesSeguretat | null>(null)
  const [error, setError] = useState(false)
  useEffect(() => { getCopiesSeguretat().then(setEstat).catch(() => setError(true)) }, [])

  const mida = (b: number) => b > 1_048_576 ? `${(b / 1_048_576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`

  return (
    <section className="bg-white rounded-xl border border-gray-200 p-6 mb-6 space-y-4">
      <h2 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Còpies de seguretat</h2>
      {error && <p className="text-sm text-red-600">No s'ha pogut consultar l'estat de les còpies.</p>}
      {estat && (
        <>
          {estat.alerta ? (
            <p role="alert" className="text-sm bg-red-50 border border-red-200 text-red-800 rounded-lg px-4 py-2">⚠ {estat.motiuAlerta}</p>
          ) : (
            <p className="text-sm bg-green-50 border border-green-200 text-green-800 rounded-lg px-4 py-2">✓ Les còpies de seguretat funcionen correctament.</p>
          )}
          {estat.disponible && estat.data && (
            <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
              <div><dt className="text-xs text-gray-500">Última còpia</dt>
                <dd className="font-medium">{new Date(estat.data).toLocaleString('ca-ES', { dateStyle: 'medium', timeStyle: 'short' })}</dd></div>
              <div><dt className="text-xs text-gray-500">Resultat</dt>
                <dd className="font-medium">{estat.resultat === 'ok' ? 'Correcta' : 'Error'}</dd></div>
              <div><dt className="text-xs text-gray-500">Mida</dt><dd className="font-medium">{mida(estat.bytes)}</dd></div>
              <div><dt className="text-xs text-gray-500">Es conserven</dt><dd className="font-medium">{estat.retencioDies} dies</dd></div>
            </dl>
          )}
          {estat.disponible && (
            <p className="text-sm">
              <span className="text-xs text-gray-500">Carpeta compartida: </span>
              {!estat.remotConfigurat
                ? <span className="text-amber-700">no configurada: les còpies només són en aquest servidor</span>
                : estat.remotResultat === 'ok'
                  ? <span className="text-green-700">✓ {estat.remotMissatge}</span>
                  : <span className="text-red-700">✗ {estat.remotMissatge}</span>}
            </p>
          )}
        </>
      )}
      <details className="text-sm text-gray-600">
        <summary className="cursor-pointer text-xs text-gray-500">Com es fan i com es restauren</summary>
        <div className="mt-2 space-y-2 text-xs">
          <p>Cada nit es fa una còpia de la base de dades i dels fitxers de dades de les preguntes a la carpeta <code>backups/</code> del servidor.
            Per protegir-les d'una avaria del disc, copia aquesta carpeta periòdicament a un altre lloc.</p>
          <p>Des del servidor, a la carpeta <code>infra/</code>:</p>
          <pre className="bg-gray-900 text-green-300 rounded p-2 overflow-x-auto">{`# fer una còpia ara
docker compose exec backup /backup/fes-copia.sh

# comprovar que una còpia es pot restaurar (no toca res)
./backup/restaura.sh --prova ../backups/bd/examplatform-….dump

# restaurar (fa abans una còpia de l'estat actual)
./backup/restaura.sh ../backups/bd/examplatform-….dump ../backups/fitxers/exam-files-….tar.gz`}</pre>
          <p>Guia completa, inclosa la configuració de la carpeta compartida: <code>docs/copies-seguretat.md</code>.</p>
        </div>
      </details>
    </section>
  )
}

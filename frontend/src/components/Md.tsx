import { useEffect, useState } from 'react'
import ReactMarkdown, { defaultUrlTransform } from 'react-markdown'
import client from '../api/client'

interface Props {
  children: string
  className?: string
  /** Imatges encara no pujades (editor): marcador `nou-N` → URL local. */
  imatgesLocals?: Record<string, string>
}

const PREFIX = 'fitxer:'
// Les imatges són a l'API (protegides amb token): es baixen una vegada i es reutilitzen
const cache = new Map<string, Promise<string>>()

function urlImatge(id: string): Promise<string> {
  let p = cache.get(id)
  if (!p) {
    p = client.get<Blob>(`/files/${id}/download`, { responseType: 'blob' })
      .then(r => URL.createObjectURL(r.data))
    p.catch(() => cache.delete(id))
    cache.set(id, p)
  }
  return p
}

function ImatgeFitxer({ id, alt, locals }: { id: string; alt: string; locals?: Record<string, string> }) {
  const local = locals?.[id]
  const [url, setUrl] = useState<string | null>(local ?? null)
  const [error, setError] = useState(false)
  useEffect(() => {
    if (local) { setUrl(local); return }
    let viu = true
    urlImatge(id).then(u => { if (viu) setUrl(u) }).catch(() => { if (viu) setError(true) })
    return () => { viu = false }
  }, [id, local])
  if (error) return <span className="text-xs text-red-500 italic">[No s'ha pogut carregar la imatge{alt ? `: ${alt}` : ''}]</span>
  if (!url) return <span className="text-xs text-gray-400 italic">Carregant imatge…</span>
  return <img src={url} alt={alt} className="max-w-full h-auto rounded border border-gray-200 my-2" />
}

export default function Md({ children, className, imatgesLocals }: Props) {
  return (
    <div className={`prose prose-sm max-w-none ${className ?? ''}`}>
      <ReactMarkdown
        // Només les imatges de l'examen (fitxer:<id>) són permeses; la resta d'URL segueixen la política per defecte
        urlTransform={url => (url.startsWith(PREFIX) ? url : defaultUrlTransform(url))}
        components={{
          // Evitem que <p> afegeixi marges excessius quan és una sola línia
          p: ({ children }) => <p className="my-1">{children}</p>,
          img: ({ src, alt }) => {
            const s = typeof src === 'string' ? src : ''
            // Una imatge externa faria que el navegador de l'alumne carregués una URL qualsevol: no es mostra
            return s.startsWith(PREFIX)
              ? <ImatgeFitxer id={s.slice(PREFIX.length)} alt={alt ?? ''} locals={imatgesLocals} />
              : <span className="text-xs text-gray-400 italic">[imatge externa no permesa{alt ? `: ${alt}` : ''}]</span>
          },
          code: ({ children }) => (
            <code className="bg-gray-100 text-gray-800 px-1 py-0.5 rounded text-xs font-mono">
              {children}
            </code>
          ),
          pre: ({ children }) => (
            <pre className="bg-gray-900 text-green-300 rounded p-3 text-xs overflow-auto my-2">
              {children}
            </pre>
          ),
        }}>
        {children}
      </ReactMarkdown>
    </div>
  )
}

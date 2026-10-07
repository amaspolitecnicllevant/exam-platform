/** Les imatges d'un enunciat s'hi referencien amb `![text](fitxer:<id>)`, on id és el d'un fitxer de la pregunta. */

const REFERENCIA = /fitxer:([0-9a-fA-F-]{36}|nou-\d+)/g
export const EXTENSIONS_IMATGE = ['png', 'jpg', 'jpeg', 'gif', 'webp']

export const referencia = (id: string) => `fitxer:${id}`

/** Ids dels fitxers que l'enunciat mostra com a imatge (inclou els marcadors `nou-N` de les imatges pendents). */
export function idsReferenciats(enunciat: string | undefined): Set<string> {
  const ids = new Set<string>()
  for (const m of (enunciat ?? '').matchAll(REFERENCIA)) ids.add(m[1])
  return ids
}

/** Markdown d'una imatge; el text alternatiu no pot trencar la sintaxi. */
export function markdownImatge(id: string, descripcio: string): string {
  const alt = descripcio.replace(/[\[\]\n\r]/g, ' ').trim()
  return `![${alt}](${referencia(id)})`
}

/** Insereix `text` a la posició del cursor (o al final si no n'hi ha), en una línia pròpia. */
export function insereix(enunciat: string, text: string, inici: number | null, fi: number | null = inici): { text: string; cursor: number } {
  const a = inici == null ? enunciat.length : Math.min(inici, enunciat.length)
  const b = fi == null ? a : Math.min(Math.max(fi, a), enunciat.length)
  const abans = enunciat.slice(0, a)
  const despres = enunciat.slice(b)
  const pre = abans === '' || abans.endsWith('\n') ? '' : '\n'
  const post = despres === '' || despres.startsWith('\n') ? '' : '\n'
  const inserit = pre + text + post
  return { text: abans + inserit + despres, cursor: abans.length + inserit.length }
}

/** Treu de l'enunciat totes les imatges que apunten a `id` (en esborrar el fitxer). */
export function treuReferencies(enunciat: string, id: string): string {
  const escapat = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return enunciat
    .replace(new RegExp(`!\\[[^\\]]*\\]\\(fitxer:${escapat}\\)\\n?`, 'g'), '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

/** Substitueix els ids provisionals (`nou-1`) pels reals un cop pujades les imatges. */
export function substitueix(enunciat: string, ids: Record<string, string>): string {
  return enunciat.replace(REFERENCIA, (tot, id: string) => (ids[id] ? referencia(ids[id]) : tot))
}

export const esImatge = (f: { contentType: string | null }) => !!f.contentType && f.contentType.startsWith('image/')

export function extensioPermesa(nom: string): boolean {
  const ext = nom.includes('.') ? nom.slice(nom.lastIndexOf('.') + 1).toLowerCase() : ''
  return EXTENSIONS_IMATGE.includes(ext)
}

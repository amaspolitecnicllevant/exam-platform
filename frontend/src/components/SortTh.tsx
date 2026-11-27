import { useState } from 'react'

export type Ordre<K extends string> = { clau: K; asc: boolean }

const collator = new Intl.Collator('ca', { sensitivity: 'base', numeric: true })

/** Estat d'ordenació d'una taula: clic a la capçalera ordena; un segon clic inverteix l'ordre. */
export function useOrdre<K extends string>(inicial: K) {
  const [ordre, setOrdre] = useState<Ordre<K>>({ clau: inicial, asc: true })
  const canvia = (clau: K) => setOrdre(o => (o.clau === clau ? { clau, asc: !o.asc } : { clau, asc: true }))
  return { ordre, canvia }
}

/** Ordena una còpia de la llista segons el valor (text o nombre) de la clau escollida. */
export function ordena<T, K extends string>(llista: T[], ordre: Ordre<K>, valor: (x: T, clau: K) => string | number): T[] {
  return [...llista].sort((a, b) => {
    const va = valor(a, ordre.clau), vb = valor(b, ordre.clau)
    const c = typeof va === 'number' && typeof vb === 'number' ? va - vb : collator.compare(String(va), String(vb))
    return ordre.asc ? c : -c
  })
}

export function SortTh<K extends string>({ clau, ordre, canvia, children, className = '' }: {
  clau: K; ordre: Ordre<K>; canvia: (k: K) => void; children: React.ReactNode; className?: string
}) {
  const actiu = ordre.clau === clau
  return (
    <th aria-sort={actiu ? (ordre.asc ? 'ascending' : 'descending') : 'none'}
      className={`px-4 py-3 text-left font-medium text-gray-600 ${className}`}>
      <button type="button" onClick={() => canvia(clau)} className="inline-flex items-center gap-1 hover:text-brand-700">
        {children}
        <span aria-hidden className={actiu ? 'text-brand-600' : 'text-gray-300'}>{actiu ? (ordre.asc ? '▲' : '▼') : '↕'}</span>
      </button>
    </th>
  )
}

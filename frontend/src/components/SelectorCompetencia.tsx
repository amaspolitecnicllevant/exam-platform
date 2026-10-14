import type { CompetenciaProg } from '../types'
import { etiquetaCompetencia } from '../utils/competencies'

interface Props {
  value: string
  onChange: (codi: string) => void
  /** Competències triables; null = encara no es poden validar (text lliure). */
  permeses: CompetenciaProg[] | null
  className?: string
  ariaLabel?: string
}

/** Tria la competència d'una pregunta entre les permeses; sense programació, text lliure. */
export default function SelectorCompetencia({ value, onChange, permeses, className = '', ariaLabel = 'Competència' }: Props) {
  if (permeses === null) {
    return (
      <input value={value} onChange={e => onChange(e.target.value)} placeholder="CO1" aria-label={ariaLabel}
        className={className} />
    )
  }
  // Un valor antic que ja no és permès es mostra igualment (marcat) perquè no desaparegui en silenci
  const desconeguda = value !== '' && !permeses.some(c => c.codi === value)
  return (
    <select value={value} onChange={e => onChange(e.target.value)} aria-label={ariaLabel} className={className}>
      <option value="">— cap —</option>
      {permeses.map(c => <option key={c.id} value={c.codi}>{etiquetaCompetencia(c)}</option>)}
      {desconeguda && <option value={value}>{value} (no permesa)</option>}
    </select>
  )
}

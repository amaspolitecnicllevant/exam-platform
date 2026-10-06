import type { Grup, Modul } from '../types'
import {
  FILTRE_GRUPS_BUIT, hiHaFiltreGrups, opcionsGrups, type FiltreGrupsAssig,
} from '../utils/filtreGrupsAssignacio'

/** Cerca i filtres de departament, cicle i mòdul per triar un grup. */
export default function FiltreGrups({ grups, moduls, filtre, onChange, visibles }: {
  grups: Grup[]
  moduls: Modul[]
  filtre: FiltreGrupsAssig
  onChange: (f: FiltreGrupsAssig) => void
  visibles: number
}) {
  const op = opcionsGrups(grups, moduls, filtre)
  const classe = 'border border-gray-300 rounded-lg px-2 py-1.5 text-sm w-full'
  return (
    <div className="space-y-2">
      <input type="search" value={filtre.text} aria-label="Cerca un grup"
        onChange={e => onChange({ ...filtre, text: e.target.value })}
        placeholder="Cerca un grup pel nom o el mòdul…"
        className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
      {op.moduls.length > 0 && (
        <div className="grid grid-cols-2 gap-2">
          {op.departaments.length > 0 && (
            <select value={filtre.departament} aria-label="Departament" className={classe}
              onChange={e => onChange({ ...filtre, departament: e.target.value, cicleId: '', modulId: '' })}>
              <option value="">Tots els departaments</option>
              {op.departaments.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
          )}
          <select value={filtre.cicleId} aria-label="Cicle" className={classe}
            onChange={e => onChange({ ...filtre, cicleId: e.target.value, modulId: '' })}>
            <option value="">Tots els cicles</option>
            {op.cicles.map(c => <option key={c.id} value={c.id}>{c.nom}</option>)}
          </select>
          <select value={filtre.modulId} aria-label="Mòdul" className={classe + ' col-span-2'}
            onChange={e => onChange({ ...filtre, modulId: e.target.value })}>
            <option value="">Tots els mòduls</option>
            {op.moduls.map(m => <option key={m.id} value={m.id}>{m.codi} — {m.nom}</option>)}
          </select>
        </div>
      )}
      <div className="flex items-center justify-between text-xs text-gray-500">
        <span>Es mostren {visibles} de {grups.length} grups</span>
        {hiHaFiltreGrups(filtre) && (
          <button type="button" onClick={() => onChange(FILTRE_GRUPS_BUIT)}
            className="hover:underline text-brand-600">Treure filtres</button>
        )}
      </div>
    </div>
  )
}

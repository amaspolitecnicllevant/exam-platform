/** Data local en format de camp <input type="date"> (AAAA-MM-DD). */
export function dataLocal(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Hora local en format de camp <input type="time"> (HH:MM). */
export function horaLocal(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** Data i hora escrites pel professor (hora local del navegador) → instant ISO en UTC per a l'API. */
export function aInstantUtc(data: string, hora: string): string {
  return new Date(`${data}T${hora}:00`).toISOString()
}

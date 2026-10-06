/** Mida màxima d'un fitxer de lliurament (ha de coincidir amb `exam.upload.max-bytes` del servidor). */
export const MIDA_MAXIMA_FITXER = 10 * 1024 * 1024

/** «1,5 MB», «320 KB»… */
export function formatMida(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  const mb = bytes / (1024 * 1024)
  return `${mb.toFixed(mb < 10 ? 1 : 0).replace('.', ',')} MB`
}

export function extensio(nom: string): string {
  const punt = nom.lastIndexOf('.')
  return punt < 0 || punt === nom.length - 1 ? '' : nom.slice(punt + 1).toLowerCase()
}

/**
 * Comprovació prèvia al navegador, perquè l'alumne vegi l'error sense esperar la pujada. El servidor
 * ho torna a comprovar tot (aquesta és només una comoditat, no una mesura de seguretat).
 * Retorna el missatge d'error o una cadena buida si el fitxer és acceptable.
 */
export function errorFitxer(nom: string, mida: number, formats: string[], maxim = MIDA_MAXIMA_FITXER): string {
  if (mida === 0) return 'El fitxer és buit.'
  const ext = extensio(nom)
  if (!ext || !formats.includes(ext)) {
    return `Aquesta pregunta només admet fitxers ${formats.map(f => '.' + f).join(', ')}.`
  }
  if (mida > maxim) return `El fitxer supera la mida màxima de ${Math.round(maxim / (1024 * 1024))} MB (en té ${formatMida(mida)}).`
  return ''
}

/** Llista de formats per mostrar: «.docx, .xlsx i .pkt». */
export function llistaFormats(formats: string[]): string {
  const f = formats.map(x => '.' + x)
  return f.length <= 1 ? f.join('') : `${f.slice(0, -1).join(', ')} i ${f[f.length - 1]}`
}

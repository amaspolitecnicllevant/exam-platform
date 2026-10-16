/** Fracció de penalització en paraules: 0.3333 → «1/3 del valor de la pregunta». */
export function fraccioPenalitzacio(factor: number): string {
  if (Math.abs(factor - 1) < 0.001) return 'el valor sencer de la pregunta'
  for (const [valor, text] of [[0.25, '1/4'], [1 / 3, '1/3'], [0.5, '1/2'], [0.2, '1/5']] as const) {
    if (Math.abs(factor - valor) < 0.002) return `${text} del valor de la pregunta`
  }
  return `el ${(factor * 100).toFixed(0)}% del valor de la pregunta`
}

/** Frase que l'alumne veu sobre les respostes errades del test, segons la penalització REAL de l'examen. */
export function textPenalitzacio(factor: number): string {
  return factor > 0
    ? `Test: cada resposta incorrecta resta ${fraccioPenalitzacio(factor)}; les respostes en blanc no resten.`
    : 'Test: les respostes incorrectes no resten; les respostes en blanc tampoc.'
}

/** Les instruccions parlen de penalització (sigui quina sigui): cal que el professor comprovi que coincideix amb l'aplicada. */
export function instruccionsParlenDePenalitzacio(instruccions: string | undefined): boolean {
  return !!instruccions && /\b(rest(a|en)|penalitz\w*|descomp\w*|descont\w*)\b/i.test(instruccions)
}

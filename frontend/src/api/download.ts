import client from './client'

/**
 * Descarrega un fitxer protegit de l'API. Un enllaç <a href> normal no envia el token
 * (és a localStorage, no en una galeta), així que es baixa amb axios i es desa des del navegador.
 * @param path ruta relativa a /api, p. ex. `/files/<id>/download`
 */
export async function descarrega(path: string, filename: string): Promise<void> {
  const res = await client.get<Blob>(path, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

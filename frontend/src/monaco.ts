/**
 * Monaco empaquetat amb l'aplicació. Per defecte @monaco-editor/react el descarrega d'un CDN
 * extern (cdn.jsdelivr.net): sense Internet a l'aula l'editor no carregaria, i s'executaria codi
 * de tercers a la pàgina de l'examen.
 */
import * as monaco from 'monaco-editor'
import { loader } from '@monaco-editor/react'
import EditorWorker from 'monaco-editor/editor/editor.worker?worker'
import HtmlWorker from 'monaco-editor/language/html/html.worker?worker'
import CssWorker from 'monaco-editor/language/css/css.worker?worker'

self.MonacoEnvironment = {
  getWorker(_workerId: string, label: string) {
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new HtmlWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new CssWorker()
    return new EditorWorker()
  },
}

loader.config({ monaco })

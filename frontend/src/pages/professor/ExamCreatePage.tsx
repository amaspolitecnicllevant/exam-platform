import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import Layout from '../../components/Layout'
import { createFromMd } from '../../api/exams'

export default function ExamCreatePage() {
  const [file, setFile]     = useState<File | null>(null)
  const [error, setError]   = useState('')
  const [loading, setLoad]  = useState(false)
  const navigate            = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file) return
    setError(''); setLoad(true)
    try {
      await createFromMd(file)
      navigate('/professor/exams')
    } catch (err: any) {
      setError(err.response?.data?.error || err.response?.data?.message || err.message || 'Error processant el fitxer')
    } finally {
      setLoad(false)
    }
  }

  return (
    <Layout>
      <div className="max-w-lg">
        <h1 className="text-2xl font-bold text-brand-700 mb-6">Nou examen des de Markdown</h1>
        <div className="bg-white rounded-xl border p-6 space-y-4">
          <p className="text-sm text-gray-600">
            Selecciona un fitxer <code>.md</code> amb el format de la sintaxi d'examens.
            La suma de punts ha de ser exactament 10.
          </p>
          <p className="text-sm text-gray-500">
            No saps com estructurar el fitxer?{' '}
            <Link to="/professor/exams/sintaxi" target="_blank"
              className="text-brand-600 hover:underline font-medium">
              Consulta la guia de format .md
            </Link>
          </p>
          <form onSubmit={handleSubmit} className="space-y-4">
            <input type="file" accept=".md" required
              onChange={e => setFile(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-gray-600 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:bg-brand-100 file:text-brand-700 hover:file:bg-brand-200" />
            {error && (
              <div className="bg-red-50 border border-red-200 rounded p-3 text-sm text-red-700">
                {error}
              </div>
            )}
            <div className="flex gap-3">
              <button type="submit" disabled={loading || !file}
                className="bg-brand-600 text-white px-5 py-2 rounded-lg text-sm hover:bg-brand-700 disabled:opacity-50">
                {loading ? 'Processant...' : 'Importar examen'}
              </button>
              <button type="button" onClick={() => navigate(-1)}
                className="text-gray-600 px-5 py-2 rounded-lg text-sm border hover:bg-gray-50">
                Cancel·lar
              </button>
            </div>
          </form>
        </div>
      </div>
    </Layout>
  )
}

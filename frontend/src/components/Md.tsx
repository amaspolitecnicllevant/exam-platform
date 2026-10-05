import ReactMarkdown from 'react-markdown'

interface Props {
  children: string
  className?: string
}

export default function Md({ children, className }: Props) {
  return (
    <div className={`prose prose-sm max-w-none ${className ?? ''}`}>
      <ReactMarkdown
        components={{
          // Evitem que <p> afegeixi marges excessius quan és una sola línia
          p: ({ children }) => <p className="my-1">{children}</p>,
          code: ({ children }) => (
            <code className="bg-gray-100 text-gray-800 px-1 py-0.5 rounded text-xs font-mono">
              {children}
            </code>
          ),
          pre: ({ children }) => (
            <pre className="bg-gray-900 text-green-300 rounded p-3 text-xs overflow-auto my-2">
              {children}
            </pre>
          ),
        }}>
        {children}
      </ReactMarkdown>
    </div>
  )
}

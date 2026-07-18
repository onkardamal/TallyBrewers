interface AlertProps {
  kind: 'error' | 'success' | 'info'
  message: string
}

/**
 * Inline status message (error / success / info).
 */
export default function Alert({ kind, message }: AlertProps) {
  const styles = {
    error: 'bg-red-50 text-red-700 border-red-200',
    success: 'bg-green-50 text-green-700 border-green-200',
    info: 'bg-gray-50 text-gray-600 border-gray-200',
  }[kind]

  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      className={`mb-4 rounded-lg border px-3 py-2 text-sm ${styles}`}
    >
      {message}
    </div>
  )
}

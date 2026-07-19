interface AlertProps {
  kind: 'error' | 'success' | 'info'
  message: string
}

const CONFIG = {
  error: {
    styles: 'bg-red-50 text-red-700 border-red-200',
    icon: 'M12 9v4m0 4h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L14.7 3.9a2 2 0 00-3.4 0z',
  },
  success: {
    styles: 'bg-accent-500/10 text-accent-700 border-accent-500/25',
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  info: {
    styles: 'bg-brand-50 text-brand-700 border-brand-200',
    icon: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  },
} as const

/**
 * Inline status message (error / success / info) with an icon.
 */
export default function Alert({ kind, message }: AlertProps) {
  const { styles, icon } = CONFIG[kind]
  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      className={`mb-4 flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm ${styles}`}
    >
      <svg
        className="mt-0.5 h-4 w-4 shrink-0"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d={icon} />
      </svg>
      <span>{message}</span>
    </div>
  )
}

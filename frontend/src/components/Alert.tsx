interface AlertProps {
  kind: 'error' | 'success' | 'info'
  message: string
}

const CONFIG = {
  error: {
    styles: 'bg-rose-50/70 text-rose-800 border-rose-100/80',
    icon: 'M12 9v4m0 4h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L14.7 3.9a2 2 0 00-3.4 0z',
  },
  success: {
    styles: 'bg-emerald-50/70 text-emerald-800 border-emerald-100/80',
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  info: {
    styles: 'bg-indigo-50/70 text-indigo-800 border-indigo-100/80',
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
      className={`mb-4 flex items-start gap-2.5 rounded-2xl border px-4 py-3.5 text-sm ${styles}`}
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

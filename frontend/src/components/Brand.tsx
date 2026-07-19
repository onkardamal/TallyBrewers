interface BrandProps {
  /** Diameter of the logo mark in pixels. */
  size?: number
  /** Show the "SecureBank" wordmark next to the mark. */
  withWordmark?: boolean
  /** Render light-on-dark (for the brand panel) vs dark-on-light. */
  variant?: 'light' | 'dark'
  className?: string
}

/**
 * SecureBank brand mark: a shield with an embedded checkmark, conveying
 * protection + verification. Used across auth screens and the brand panel.
 */
export default function Brand({
  size = 40,
  withWordmark = false,
  variant = 'dark',
  className = '',
}: BrandProps) {
  const wordmarkColor = variant === 'light' ? 'text-white' : 'text-brand-900'

  return (
    <div className={`flex items-center gap-3 ${className}`}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 48 48"
        fill="none"
        aria-hidden="true"
        className="shrink-0"
      >
        <defs>
          <linearGradient id="sb-shield" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3f54bf" />
            <stop offset="100%" stopColor="#131a44" />
          </linearGradient>
        </defs>
        <path
          d="M24 3l16 6v11c0 10.5-6.8 18.4-16 22-9.2-3.6-16-11.5-16-22V9l16-6z"
          fill="url(#sb-shield)"
        />
        <path
          d="M24 3l16 6v11c0 10.5-6.8 18.4-16 22V3z"
          fill="#0a0e29"
          opacity="0.18"
        />
        <path
          d="M16.5 24.5l5 5 10-11"
          stroke="#34d399"
          strokeWidth="3.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {withWordmark && (
        <span className={`text-xl font-semibold tracking-tight ${wordmarkColor}`}>
          Secure<span className="text-accent-500">Bank</span>
        </span>
      )}
    </div>
  )
}

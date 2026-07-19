import type { ButtonHTMLAttributes, ReactNode } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost'
  loading?: boolean
  /** Optional leading icon (e.g. a passkey glyph). */
  icon?: ReactNode
}

/**
 * Primary/secondary/ghost action button with brand styling, focus rings, and
 * an inline spinner while loading.
 */
export default function Button({
  variant = 'primary',
  loading = false,
  icon,
  children,
  disabled,
  className = '',
  ...rest
}: ButtonProps) {
  const base =
    'inline-flex w-full items-center justify-center gap-2 rounded-2xl px-5 py-3.5 ' +
    'text-sm font-semibold transition-all duration-200 focus:outline-none ' +
    'focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-brand-500 ' +
    'disabled:cursor-not-allowed disabled:opacity-60 active:scale-[0.98]'

  const styles = {
    primary:
      'bg-brand-900 text-white hover:bg-brand-950 ' +
      'shadow-[0_4px_12px_rgba(0,0,0,0.08)] shadow-brand-950/10 border border-brand-950',
    secondary:
      'bg-white text-brand-800 border border-gray-200 hover:bg-gray-50 ' +
      'shadow-[0_1px_2px_rgba(0,0,0,0.02)]',
    ghost: 'bg-transparent text-brand-700 hover:bg-gray-50',
  }[variant]

  return (
    <button
      className={`${base} ${styles} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? (
        <>
          <svg
            className="h-4 w-4 animate-spin"
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-90"
              fill="currentColor"
              d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
            />
          </svg>
          Please wait…
        </>
      ) : (
        <>
          {icon}
          {children}
        </>
      )}
    </button>
  )
}

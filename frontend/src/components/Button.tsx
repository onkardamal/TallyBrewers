import type { ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
  loading?: boolean
}

/**
 * Primary/secondary action button. Minimal, calm styling per UI_GUIDELINES.
 */
export default function Button({
  variant = 'primary',
  loading = false,
  children,
  disabled,
  className = '',
  ...rest
}: ButtonProps) {
  const base =
    'w-full rounded-lg px-4 py-2.5 text-sm font-medium transition-colors ' +
    'focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-900 ' +
    'disabled:opacity-50 disabled:cursor-not-allowed'
  const styles =
    variant === 'primary'
      ? 'bg-gray-900 text-white hover:bg-gray-800'
      : 'bg-white text-gray-900 border border-gray-300 hover:bg-gray-50'

  return (
    <button
      className={`${base} ${styles} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading ? 'Please wait…' : children}
    </button>
  )
}

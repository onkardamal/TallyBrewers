import type { InputHTMLAttributes, ReactNode } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Optional leading icon rendered inside the field. */
  icon?: ReactNode
  hint?: string
}

/**
 * Labelled text input with accessible label association, optional leading
 * icon, and brand focus styling.
 */
export default function TextField({
  label,
  id,
  icon,
  hint,
  className = '',
  ...rest
}: TextFieldProps) {
  const fieldId = id ?? rest.name ?? label.toLowerCase().replace(/\s+/g, '-')
  return (
    <div className="text-left">
      <label
        htmlFor={fieldId}
        className="mb-1.5 block text-sm font-medium text-brand-900"
      >
        {label}
      </label>
      <div className="relative">
        {icon && (
          <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-gray-400">
            {icon}
          </span>
        )}
        <input
          id={fieldId}
          className={`w-full rounded-2xl border border-gray-200 bg-gray-50/50 py-3.5 text-sm
            text-brand-950 placeholder-gray-400 transition-all duration-200
            focus:border-brand-900 focus:bg-white focus:outline-none focus:ring-4 focus:ring-brand-900/10
            disabled:bg-gray-100 disabled:text-gray-400 disabled:cursor-not-allowed
            ${icon ? 'pl-11 pr-4' : 'px-4'} ${className}`}
          {...rest}
        />
      </div>
      {hint && <p className="mt-1.5 text-xs text-gray-400">{hint}</p>}
    </div>
  )
}

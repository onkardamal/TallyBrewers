import type { InputHTMLAttributes } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
}

/**
 * Labelled text input with accessible association between label and field.
 */
export default function TextField({ label, id, ...rest }: TextFieldProps) {
  const fieldId = id ?? rest.name ?? label.toLowerCase().replace(/\s+/g, '-')
  return (
    <div className="text-left">
      <label
        htmlFor={fieldId}
        className="mb-1 block text-sm font-medium text-gray-700"
      >
        {label}
      </label>
      <input
        id={fieldId}
        className="w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm
          text-gray-900 placeholder-gray-400 focus:border-gray-900
          focus:outline-none focus:ring-1 focus:ring-gray-900"
        {...rest}
      />
    </div>
  )
}

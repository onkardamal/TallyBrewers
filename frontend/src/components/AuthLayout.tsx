import type { ReactNode } from 'react'

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
}

/**
 * Centered single-column layout shared by all auth screens. Minimal and
 * calm, per UI_GUIDELINES (Apple/Stripe/Google inspired).
 */
export default function AuthLayout({
  title,
  subtitle,
  children,
}: AuthLayoutProps) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <p className="text-sm font-semibold tracking-wide text-gray-500 uppercase">
            SecureBank
          </p>
          <h1 className="mt-2 text-2xl font-medium text-gray-900">{title}</h1>
          {subtitle && <p className="mt-2 text-sm text-gray-500">{subtitle}</p>}
        </div>
        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          {children}
        </div>
      </div>
    </main>
  )
}

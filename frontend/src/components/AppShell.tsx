import type { ReactNode } from 'react'
import Brand from './Brand'

interface AppShellProps {
  userName?: string
  userEmail?: string
  onSignOut?: () => void
  children: ReactNode
}

/**
 * Authenticated application shell: a sticky top bar with brand + account menu,
 * over a calm neutral canvas. Used by signed-in surfaces like the dashboard.
 */
export default function AppShell({
  userName,
  userEmail,
  onSignOut,
  children,
}: AppShellProps) {
  const initials = (userName ?? '?')
    .split(' ')
    .map((p) => p[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="sticky top-0 z-20 border-b border-gray-200 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-5 py-3.5">
          <Brand size={30} withWordmark />
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-brand-900">{userName}</p>
              <p className="text-xs text-gray-400">{userEmail}</p>
            </div>
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 text-xs font-semibold text-white">
              {initials}
            </div>
            {onSignOut && (
              <button
                onClick={onSignOut}
                className="rounded-lg px-3 py-1.5 text-sm font-medium text-gray-500 transition hover:bg-gray-100 hover:text-brand-700"
              >
                Sign out
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-5 py-8 sb-rise">{children}</main>
    </div>
  )
}

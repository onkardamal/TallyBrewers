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
    <div className="min-h-screen bg-slate-50/50">
      <header className="sticky top-0 z-20 border-b border-slate-200/80 bg-white/70 backdrop-blur-md">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-5 py-4">
          <Brand size={28} withWordmark />
          <div className="flex items-center gap-4">
            <div className="hidden text-right sm:block">
              <p className="font-display text-sm font-semibold text-slate-800">{userName}</p>
              <p className="text-xs text-slate-400">{userEmail}</p>
            </div>
            <div className="flex h-9 w-9 items-center justify-center rounded-2xl bg-brand-900 text-xs font-semibold text-white shadow-sm ring-2 ring-white">
              {initials}
            </div>
            {onSignOut && (
              <button
                onClick={onSignOut}
                className="rounded-xl px-3 py-2 text-sm font-semibold text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 active:scale-95"
              >
                Sign out
              </button>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-5 py-10 sb-rise">{children}</main>
    </div>
  )
}

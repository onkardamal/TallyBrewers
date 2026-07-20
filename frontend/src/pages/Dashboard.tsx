import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AppShell from '../components/AppShell'
import SessionList from '../components/SessionList'
import { getMe, logout } from '../api/auth'
import type { UserDto } from '../api/auth'
import { setAccessToken } from '../api/client'

export default function Dashboard() {
  const [user, setUser] = useState<UserDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    getMe()
      .then((data) => {
        setUser(data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
        navigate('/')
      })
  }, [navigate])

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // Ignore network errors on logout
    } finally {
      setAccessToken(null)
      navigate('/', { state: { loggedOut: true } })
    }
  }

  if (loading) {
    return (
      <AppShell>
        <div className="space-y-4">
          <div className="h-8 w-64 animate-pulse rounded-lg bg-gray-200" />
          <div className="h-40 animate-pulse rounded-2xl bg-gray-100" />
        </div>
      </AppShell>
    )
  }

  const firstName = user?.name?.split(' ')[0]

  return (
    <AppShell userName={user?.name} userEmail={user?.email} onSignOut={handleLogout}>
      {/* Warm, reassuring welcome */}
      <div className="mb-8">
        <h1 className="font-display text-3xl font-bold tracking-tight text-slate-900">
          Welcome back{firstName ? `, ${firstName}` : ''}
        </h1>
        <p className="mt-1.5 text-sm text-slate-500 font-medium">Your SecureBank account is active and protected.</p>
      </div>

      {error && <p className="mb-4 text-sm text-rose-600">{error}</p>}

      {/* Protection reassurance — human language, not a data grid */}
      <div className="mb-8 overflow-hidden rounded-3xl border border-emerald-100 bg-gradient-to-br from-emerald-50/50 via-white to-transparent sb-premium-shadow">
        <div className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:gap-6">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-600 border border-emerald-100/50">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
              <path d="M9 12l2 2 4-4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div className="flex-1">
            <p className="font-display text-base font-semibold text-slate-900">Your account is protected</p>
            <p className="mt-1 text-sm text-slate-500 leading-relaxed">
              You are signed in with a passkey — no passwords to steal, phish, or leak. Your email has been verified, and your secure recovery codes are active.
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Account details — simple, human */}
        <section className="rounded-3xl bg-white p-6 sb-premium-border sb-premium-shadow lg:col-span-1">
          <h2 className="font-display text-lg font-bold text-slate-900">Your details</h2>
          <dl className="mt-5 space-y-4 text-sm">
            <div className="border-b border-slate-50 pb-3">
              <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">Name</dt>
              <dd className="mt-1.5 font-semibold text-slate-800 text-base">{user?.name}</dd>
            </div>
            <div className="border-b border-slate-50 pb-3">
              <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">Email</dt>
              <dd className="mt-1.5 break-all font-medium text-slate-700">{user?.email}</dd>
            </div>
            {user?.phone && (
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wider text-slate-400">Phone</dt>
                <dd className="mt-1.5 font-medium text-slate-700">{user.phone}</dd>
              </div>
            )}
          </dl>
        </section>

        {/* Where you're signed in */}
        <div className="lg:col-span-2">
          <SessionList />
        </div>
      </div>
    </AppShell>
  )
}

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
      navigate('/')
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
        <h1 className="text-2xl font-semibold tracking-tight text-brand-950">
          Welcome back{firstName ? `, ${firstName}` : ''}
        </h1>
        <p className="mt-1 text-gray-500">You&apos;re signed in securely.</p>
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {/* Protection reassurance — human language, not a data grid */}
      <div className="mb-6 overflow-hidden rounded-2xl border border-accent-500/20 bg-gradient-to-br from-accent-500/[0.07] to-transparent">
        <div className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:gap-6">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-accent-500/12 text-accent-700">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
              <path d="M9 12l2 2 4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <div className="flex-1">
            <p className="font-semibold text-brand-950">Your account is protected</p>
            <p className="mt-0.5 text-sm text-gray-500">
              Signed in with a passkey — no password to steal or phish. Your email is verified and
              your recovery codes are saved.
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Account details — simple, human */}
        <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm lg:col-span-1">
          <h2 className="text-base font-semibold text-brand-950">Your details</h2>
          <dl className="mt-4 space-y-4 text-sm">
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-gray-400">Name</dt>
              <dd className="mt-1 font-medium text-brand-950">{user?.name}</dd>
            </div>
            <div>
              <dt className="text-xs font-medium uppercase tracking-wide text-gray-400">Email</dt>
              <dd className="mt-1 break-all font-medium text-brand-950">{user?.email}</dd>
            </div>
            {user?.phone && (
              <div>
                <dt className="text-xs font-medium uppercase tracking-wide text-gray-400">Phone</dt>
                <dd className="mt-1 font-medium text-brand-950">{user.phone}</dd>
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

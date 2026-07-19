import { useEffect, useState } from 'react'
import { listSessions, revokeSession, type SessionDto } from '../api/auth'
import { parseUserAgent, relativeTime } from '../lib/device'
import Alert from './Alert'

function DeviceGlyph({ kind }: { kind: string }) {
  if (kind === 'mobile' || kind === 'tablet') {
    return (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <rect x="7" y="2" width="10" height="20" rx="2.5" stroke="currentColor" strokeWidth="1.7" />
        <path d="M11 18h2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    )
  }
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="4" width="18" height="12" rx="2" stroke="currentColor" strokeWidth="1.7" />
      <path d="M8 20h8M12 16v4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}

/**
 * "Devices & sessions" security panel: lists the user's active sessions from
 * real persisted data and lets them remotely revoke any device.
 */
export default function SessionList() {
  const [sessions, setSessions] = useState<SessionDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revoking, setRevoking] = useState<number | null>(null)

  async function load() {
    try {
      setError(null)
      setSessions(await listSessions())
    } catch (err: any) {
      setError(err.message ?? 'Could not load your sessions.')
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function handleRevoke(id: number) {
    setRevoking(id)
    try {
      await revokeSession(id)
      setSessions((prev) => (prev ? prev.filter((s) => s.id !== id) : prev))
    } catch (err: any) {
      setError(err.message ?? 'Could not revoke that session.')
    } finally {
      setRevoking(null)
    }
  }

  return (
    <section className="rounded-3xl bg-white p-6 sb-premium-border sb-premium-shadow">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-bold text-slate-900">Where you&apos;re signed in</h2>
          <p className="mt-1 text-sm text-slate-500">
            Your active sessions. If you don&apos;t recognise one, sign it out.
          </p>
        </div>
        {sessions && (
          <span className="rounded-2xl bg-slate-100 border border-slate-200/50 px-2.5 py-1 text-xs font-semibold text-slate-600">
            {sessions.length} active
          </span>
        )}
      </div>

      {error && <Alert kind="error" message={error} />}

      {sessions === null && !error && (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <div key={i} className="h-16 animate-pulse rounded-2xl bg-slate-50 border border-slate-100" />
          ))}
        </div>
      )}

      <ul className="space-y-3">
        {sessions?.map((s) => {
          const device = parseUserAgent(s.userAgent)
          return (
            <li
              key={s.id}
              className="flex items-center gap-4 rounded-2xl border border-slate-100 bg-slate-50/50 p-4 transition-all duration-200 hover:bg-slate-50"
            >
              <div
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border ${
                  s.current 
                    ? 'bg-emerald-50 text-emerald-600 border-emerald-100/50' 
                    : 'bg-slate-100 text-slate-600 border-slate-200/30'
                }`}
              >
                <DeviceGlyph kind={device.kind} />
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-semibold text-slate-800">{device.label}</p>
                  {s.current && (
                    <span className="rounded-full bg-emerald-500/10 border border-emerald-500/25 px-2.5 py-0.5 text-[10px] font-bold text-emerald-700">
                      This device
                    </span>
                  )}
                </div>
                <p className="mt-1 truncate text-xs text-slate-400 font-medium">
                  {s.ipAddress ?? 'Unknown IP'} · active {relativeTime(s.lastActivity)}
                </p>
              </div>

              {s.current ? (
                <span className="shrink-0 text-xs font-bold uppercase tracking-wider text-slate-400">Current</span>
              ) : (
                <button
                  onClick={() => handleRevoke(s.id)}
                  disabled={revoking === s.id}
                  className="shrink-0 rounded-xl border border-rose-100 bg-white px-3.5 py-2 text-xs font-semibold text-rose-600 transition duration-150 hover:bg-rose-50 hover:text-rose-700 active:scale-95 disabled:opacity-50"
                >
                  {revoking === s.id ? 'Revoking…' : 'Sign out'}
                </button>
              )}
            </li>
          )
        })}
      </ul>

      {sessions?.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-400">No other active sessions.</p>
      )}
    </section>
  )
}

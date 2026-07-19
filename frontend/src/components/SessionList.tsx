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
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-brand-950">Where you&apos;re signed in</h2>
          <p className="mt-0.5 text-sm text-gray-500">
            Your active devices. If you don&apos;t recognise one, sign it out.
          </p>
        </div>
        {sessions && (
          <span className="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700">
            {sessions.length} active
          </span>
        )}
      </div>

      {error && <Alert kind="error" message={error} />}

      {sessions === null && !error && (
        <div className="space-y-3">
          {[0, 1].map((i) => (
            <div key={i} className="h-16 animate-pulse rounded-xl bg-gray-100" />
          ))}
        </div>
      )}

      <ul className="space-y-3">
        {sessions?.map((s) => {
          const device = parseUserAgent(s.userAgent)
          return (
            <li
              key={s.id}
              className="flex items-center gap-4 rounded-xl border border-gray-100 bg-gray-50/60 p-4"
            >
              <div
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${
                  s.current ? 'bg-accent-500/12 text-accent-700' : 'bg-brand-50 text-brand-600'
                }`}
              >
                <DeviceGlyph kind={device.kind} />
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <p className="truncate text-sm font-medium text-brand-950">{device.label}</p>
                  {s.current && (
                    <span className="rounded-full bg-accent-500/15 px-2 py-0.5 text-[11px] font-semibold text-accent-700">
                      This device
                    </span>
                  )}
                </div>
                <p className="mt-0.5 truncate text-xs text-gray-500">
                  {s.ipAddress ?? 'Unknown IP'} · active {relativeTime(s.lastActivity)}
                </p>
              </div>

              {s.current ? (
                <span className="shrink-0 text-xs text-gray-400">Current</span>
              ) : (
                <button
                  onClick={() => handleRevoke(s.id)}
                  disabled={revoking === s.id}
                  className="shrink-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs font-medium text-red-600 transition hover:border-red-200 hover:bg-red-50 disabled:opacity-50"
                >
                  {revoking === s.id ? 'Revoking…' : 'Revoke'}
                </button>
              )}
            </li>
          )
        })}
      </ul>

      {sessions?.length === 0 && (
        <p className="py-6 text-center text-sm text-gray-400">No other active sessions.</p>
      )}
    </section>
  )
}

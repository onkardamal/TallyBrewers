import type { ReactNode } from 'react'
import Brand from './Brand'

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
  /** Optional node rendered under the card (links, secondary actions). */
  footer?: ReactNode
}

const TRUST_POINTS = [
  {
    title: 'Passwordless by design',
    body: 'No passwords to phish, leak, or reuse. Your identity is a key only your device holds.',
  },
  {
    title: 'Phishing-resistant passkeys',
    body: 'Credentials are bound to securebank.com — a fake site physically cannot use them.',
  },
  {
    title: 'Bank-grade sessions',
    body: 'Short-lived tokens, rotating refresh, and full visibility into every device.',
  },
]

/**
 * Two-column auth shell: a branded, reassuring marketing panel on the left and
 * the focused auth surface on the right. Collapses to a single column with a
 * compact brand header on small screens.
 */
export default function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: AuthLayoutProps) {
  return (
    <main className="flex min-h-screen bg-gray-50/30">
      {/* Brand / trust panel */}
      <aside className="relative hidden w-[46%] shrink-0 overflow-hidden sb-mesh-gradient lg:flex lg:flex-col">
        {/* Decorative grid + glow */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage:
              'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)',
            backgroundSize: '44px 44px',
          }}
        />

        <div className="relative z-10 flex h-full flex-col justify-between p-12">
          <Brand size={40} withWordmark variant="light" />

          <div className="max-w-md">
            <h2 className="font-display text-4xl font-semibold leading-tight text-white">
              Banking security,
              <br />
              without the password.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-slate-300">
              SecureBank replaces the weakest link in banking — the password —
              with passkeys built into your devices.
            </p>

            <ul className="mt-10 space-y-6">
              {TRUST_POINTS.map((p) => (
                <li key={p.title} className="flex gap-4">
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/5 ring-1 ring-white/10">
                    <svg
                      width="18"
                      height="18"
                      viewBox="0 0 24 24"
                      fill="none"
                      aria-hidden="true"
                    >
                      <path
                        d="M20 6L9 17l-5-5"
                        stroke="#10b981"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-white">{p.title}</p>
                    <p className="mt-1 text-sm leading-relaxed text-slate-400">
                      {p.body}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </div>

          <div className="flex items-center gap-2 text-xs text-slate-500">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinejoin="round"
              />
            </svg>
            256-bit TLS · FIDO2 / WebAuthn · SOC 2-aligned controls
          </div>
        </div>
      </aside>

      {/* Auth surface */}
      <section className="flex flex-1 items-center justify-center bg-gray-50/50 px-5 py-12 sm:px-8">
        <div className="w-full max-w-md rounded-3xl bg-white p-8 sm:p-10 sb-premium-border sb-premium-shadow sb-rise">
          {/* Compact brand header (mobile only) */}
          <div className="mb-8 flex justify-center lg:hidden">
            <Brand size={32} withWordmark />
          </div>

          <div className="mb-6 text-left">
            <h1 className="font-display text-2xl font-bold tracking-tight text-brand-900 sm:text-3xl">
              {title}
            </h1>
            {subtitle && (
              <p className="mt-2 text-sm leading-relaxed text-brand-500">
                {subtitle}
              </p>
            )}
          </div>

          {children}

          {footer && <div className="mt-6 border-t border-gray-100 pt-6">{footer}</div>}
        </div>
      </section>
    </main>
  )
}

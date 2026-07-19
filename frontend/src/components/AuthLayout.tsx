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
    <main className="flex min-h-screen bg-white">
      {/* Brand / trust panel */}
      <aside className="relative hidden w-[46%] shrink-0 overflow-hidden bg-brand-950 lg:flex lg:flex-col">
        <div
          className="pointer-events-none absolute inset-0 opacity-90"
          style={{
            background:
              'radial-gradient(120% 80% at 15% 10%, #263283 0%, rgba(38,50,131,0) 55%), radial-gradient(90% 70% at 90% 90%, #10b981 0%, rgba(16,185,129,0) 45%), linear-gradient(160deg, #131a44 0%, #0a0e29 100%)',
          }}
        />
        {/* Decorative grid + glow */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)',
            backgroundSize: '44px 44px',
          }}
        />

        <div className="relative z-10 flex h-full flex-col justify-between p-12">
          <Brand size={40} withWordmark variant="light" />

          <div className="max-w-md">
            <h2 className="text-3xl font-semibold leading-tight text-white">
              Banking security,
              <br />
              without the password.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-brand-200">
              SecureBank replaces the weakest link in banking — the password —
              with passkeys built into your devices.
            </p>

            <ul className="mt-10 space-y-6">
              {TRUST_POINTS.map((p) => (
                <li key={p.title} className="flex gap-4">
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10 ring-1 ring-white/15">
                    <svg
                      width="18"
                      height="18"
                      viewBox="0 0 24 24"
                      fill="none"
                      aria-hidden="true"
                    >
                      <path
                        d="M20 6L9 17l-5-5"
                        stroke="#34d399"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-white">{p.title}</p>
                    <p className="mt-1 text-sm leading-relaxed text-brand-200">
                      {p.body}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </div>

          <div className="flex items-center gap-2 text-xs text-brand-300">
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
      <section className="flex flex-1 items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-sm sb-rise">
          {/* Compact brand header (mobile only) */}
          <div className="mb-8 flex justify-center lg:hidden">
            <Brand size={36} withWordmark />
          </div>

          <div className="mb-7">
            <h1 className="text-2xl font-semibold tracking-tight text-brand-950">
              {title}
            </h1>
            {subtitle && (
              <p className="mt-2 text-sm leading-relaxed text-gray-500">
                {subtitle}
              </p>
            )}
          </div>

          {children}

          {footer && <div className="mt-6">{footer}</div>}
        </div>
      </section>
    </main>
  )
}

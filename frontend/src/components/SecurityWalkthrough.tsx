import { useEffect, useState } from 'react'

interface Step {
  title: string
  body: string
  render: (active: boolean) => React.ReactNode
}

/**
 * Animated, auto-advancing explainer for how passkeys protect the user. Purely
 * illustrative motion (no real data) — designed to make the "why passwordless
 * is safer" story land during onboarding and in demos.
 */
const STEPS: Step[] = [
  {
    title: 'A key is born on your device',
    body: 'Setting up creates a private key that never leaves this device — locked behind your fingerprint, face, or PIN.',
    render: (active) => (
      <g>
        <rect x="70" y="55" width="60" height="90" rx="10" stroke="#8fa1e2" strokeWidth="2.5" fill="#131a44" />
        <circle cx="100" cy="100" r="18" fill="none" stroke="#34d399" strokeWidth="3" className={active ? 'sb-pulse-ring' : ''} />
        <path d="M94 100l4 4 8-9" stroke="#34d399" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>
    ),
  },
  {
    title: 'Nothing secret is ever sent',
    body: 'Only a public key goes to SecureBank. There is no password or shared secret to intercept, leak, or phish.',
    render: (active) => (
      <g>
        <rect x="40" y="80" width="40" height="40" rx="8" fill="#131a44" stroke="#8fa1e2" strokeWidth="2" />
        <rect x="120" y="80" width="40" height="40" rx="8" fill="#0a0e29" stroke="#3f54bf" strokeWidth="2" />
        <path
          d="M82 100h36"
          stroke="#34d399"
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray="6 6"
          style={active ? { animation: 'sb-float 3s ease-in-out infinite' } : undefined}
        />
        <path d="M112 94l8 6-8 6" stroke="#34d399" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>
    ),
  },
  {
    title: 'Phishing simply cannot work',
    body: 'Your passkey is bound to securebank.com. A look-alike site literally cannot use it — the browser refuses.',
    render: (active) => (
      <g>
        <circle cx="100" cy="100" r="34" fill="none" stroke="#ef4444" strokeWidth="3" opacity={active ? 1 : 0.4} />
        <path d="M78 78l44 44" stroke="#ef4444" strokeWidth="3" strokeLinecap="round" />
        <path
          d="M100 72l18 7v12c0 11-7.6 18-18 22-10.4-4-18-11-18-22V79l18-7z"
          fill="#131a44"
          stroke="#8fa1e2"
          strokeWidth="2"
        />
      </g>
    ),
  },
]

export default function SecurityWalkthrough() {
  const [step, setStep] = useState(0)

  useEffect(() => {
    const t = setInterval(() => setStep((s) => (s + 1) % STEPS.length), 3800)
    return () => clearInterval(t)
  }, [])

  const current = STEPS[step]

  return (
    <div className="rounded-2xl border border-gray-100 bg-gradient-to-br from-brand-950 to-brand-800 p-6 text-center">
      <svg viewBox="0 0 200 200" className="mx-auto h-40 w-40" aria-hidden="true">
        {current.render(true)}
      </svg>
      <h3 key={`t-${step}`} className="sb-rise text-base font-semibold text-white">
        {current.title}
      </h3>
      <p key={`b-${step}`} className="sb-rise mx-auto mt-2 max-w-xs text-sm leading-relaxed text-brand-200">
        {current.body}
      </p>
      <div className="mt-5 flex justify-center gap-2">
        {STEPS.map((_, i) => (
          <button
            key={i}
            onClick={() => setStep(i)}
            aria-label={`Step ${i + 1}`}
            className={`h-1.5 rounded-full transition-all ${
              i === step ? 'w-6 bg-accent-400' : 'w-1.5 bg-white/25'
            }`}
          />
        ))}
      </div>
    </div>
  )
}

import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'
import Button from '../components/Button'
import { startRecovery, verifyRecovery, completeRecovery } from '../api/auth'
import { createPasskey } from '../api/webauthn'
import { setAccessToken } from '../api/client'

export default function Recover() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const navigate = useNavigate()

  // Mode 1: Request Recovery Link
  const [email, setEmail] = useState(searchParams.get('email') ?? '')
  const [loading, setLoading] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Mode 2: Verify and Register New Passkey
  const [recoveryCode, setRecoveryCode] = useState('')
  const [showContactDialog, setShowContactDialog] = useState(false)
  const [contactReason, setContactReason] = useState<'recovery_codes' | 'email'>('recovery_codes')
  const [showRecoveryContact, setShowRecoveryContact] = useState(false)
  const [showEmailContact, setShowEmailContact] = useState(false)

  async function handleRequestLink(e: React.FormEvent) {
    e.preventDefault()
    if (!email) return

    setLoading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const res = await startRecovery(email)
      setSuccessMessage(res.message ?? 'If the account exists, a recovery link has been sent.')
    } catch (err: any) {
      setError(err.message ?? 'Failed to request recovery. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleVerifyAndRecover(e: React.FormEvent) {
    e.preventDefault()
    if (!email || !token || !recoveryCode) return

    setLoading(true)
    setError(null)

    try {
      // 1. Verify recovery details
      const verifyRes = await verifyRecovery(email, token, recoveryCode)

      // 2. Parse passkey options
      const parsedOptions = JSON.parse(verifyRes.creationOptionsJson)

      // 3. Register new passkey
      const credential = await createPasskey({ publicKey: parsedOptions.publicKey })

      // 4. Complete recovery and establish session
      const completeRes = await completeRecovery(verifyRes.handle, credential)

      // 5. Save new token and navigate to show new recovery codes
      setAccessToken(completeRes.accessToken)
      navigate('/recovery-codes', { state: { recoveryCodes: completeRes.recoveryCodes } })
    } catch (err: any) {
      setError(err.message ?? 'Account recovery failed. Please verify your details.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      {token ? (
        // Mode 2: Verification and Passkey Reset form
        <AuthLayout title="Recover account" subtitle="Enter one of your saved recovery codes to register a new passkey.">
          <form onSubmit={handleVerifyAndRecover} className="space-y-4">
            {email ? (
              <div className="rounded-2xl bg-emerald-50/40 p-4 border border-emerald-100/50 flex items-center gap-3">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-emerald-100/60 text-emerald-600">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                    <polyline points="22 4 12 14.01 9 11.01" />
                  </svg>
                </div>
                <div className="min-w-0 flex-1 text-left">
                  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">Email Verified</p>
                  <p className="text-sm text-slate-700 font-semibold truncate">{email}</p>
                </div>
              </div>
            ) : (
              <div>
                <label htmlFor="email" className="sr-only">
                  Email address
                </label>
                <input
                  id="email"
                  type="email"
                  required
                  placeholder="Confirm email address"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  disabled={loading}
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 disabled:opacity-50"
                />
              </div>
            )}

            <div>
              <label htmlFor="recoveryCode" className="sr-only">
                Recovery code
              </label>
              <input
                id="recoveryCode"
                type="text"
                required
                placeholder="Recovery code"
                value={recoveryCode}
                onChange={(e) => setRecoveryCode(e.target.value)}
                disabled={loading}
                className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 disabled:opacity-50 font-mono"
              />
            </div>

            <div className="text-left mt-2">
              <button
                type="button"
                onClick={() => setShowRecoveryContact(!showRecoveryContact)}
                className="text-xs text-brand-600 hover:text-brand-700 hover:underline font-semibold transition"
              >
                Lost your recovery codes?
              </button>
              {showRecoveryContact && (
                <div className="mt-3 rounded-2xl bg-amber-50/70 p-4 border border-amber-100/80 sb-rise">
                  <p className="text-xs text-amber-800 leading-relaxed font-medium">
                    If you have lost both your passkey and recovery codes, automatic recovery is not possible. Please contact your bank to complete identity verification.
                  </p>
                  <div className="mt-3">
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => {
                        setContactReason('recovery_codes')
                        setShowContactDialog(true)
                      }}
                      className="!py-2 !px-4 text-xs font-semibold rounded-xl"
                    >
                      Contact Bank
                    </Button>
                  </div>
                </div>
              )}
            </div>

            {error && <Alert kind="error" message={error} />}

            <Button type="submit" loading={loading}>
              Verify & Create New Passkey
            </Button>
          </form>

          <p className="mt-4 text-center text-sm text-gray-500">
            Remembered your passkey?{' '}
            <Link to="/" className="font-medium text-gray-900 hover:underline">
              Go back to sign in
            </Link>
          </p>
        </AuthLayout>
      ) : (
        // Mode 1: Request Link form
        <AuthLayout title="Recover account" subtitle="Enter your email address and we'll send you a recovery link.">
          <form onSubmit={handleRequestLink} className="space-y-4">
            <div>
              <label htmlFor="email" className="sr-only">
                Email address
              </label>
              <input
                id="email"
                type="email"
                required
                placeholder="Email address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
                className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 disabled:opacity-50"
              />
            </div>

            {successMessage && <Alert kind="success" message={successMessage} />}
            {error && <Alert kind="error" message={error} />}

            <Button type="submit" loading={loading}>
              Send recovery link
            </Button>
          </form>

          <div className="text-center mt-4">
            <button
              type="button"
              onClick={() => setShowEmailContact(!showEmailContact)}
              className="text-xs text-brand-600 hover:text-brand-700 hover:underline font-semibold transition"
            >
              Lost email access?
            </button>
            {showEmailContact && (
              <div className="mt-3 text-left rounded-2xl bg-amber-50/70 p-4 border border-amber-100/80 sb-rise">
                <p className="text-xs text-amber-800 leading-relaxed font-medium">
                  If you have lost access to your registered email address, automatic recovery is not possible. Please contact your bank to complete identity verification.
                </p>
                <div className="mt-3">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => {
                      setContactReason('email')
                      setShowContactDialog(true)
                    }}
                    className="!py-2 !px-4 text-xs font-semibold rounded-xl"
                  >
                    Contact Bank
                  </Button>
                </div>
              </div>
            )}
          </div>

          <p className="mt-4 text-center text-sm text-gray-500">
            Remembered your passkey?{' '}
            <Link to="/" className="font-medium text-gray-900 hover:underline">
              Go back to sign in
            </Link>
          </p>
        </AuthLayout>
      )}

      {/* Manual Recovery Dialog */}
      {showContactDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-md overflow-hidden rounded-3xl bg-white p-6 shadow-xl border border-gray-100 sb-rise">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-50 text-amber-600 border border-amber-100/50 mb-4">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </div>
            <h3 className="font-display text-lg font-bold text-slate-900">Manual Account Recovery</h3>
            <p className="mt-3 text-sm text-slate-500 leading-relaxed font-medium">
              {contactReason === 'email' ? (
                "If you have lost access to your registered email address, automatic recovery is not possible. Please contact your bank to complete identity verification. After successful verification, the bank can assist you with securely resetting your authentication credentials."
              ) : (
                "If you have lost both your passkey and recovery codes, automatic recovery is not possible. Please contact your bank to complete identity verification. After successful verification, the bank can assist you with securely resetting your authentication credentials."
              )}
            </p>
            
            <div className="mt-5 rounded-2xl bg-slate-50 p-4 text-sm text-slate-600 space-y-2.5 border border-slate-100 font-medium">
              <div className="flex items-center gap-2.5">
                <svg className="h-4 w-4 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
                <span className="text-slate-800 font-semibold">+1 (800) 555-0199</span>
              </div>
              <div className="flex items-center gap-2.5">
                <svg className="h-4 w-4 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
                <span className="text-slate-800 font-semibold">support@securebank.local</span>
              </div>
            </div>

            <div className="mt-6 flex gap-3">
              <Button type="button" variant="secondary" onClick={() => setShowContactDialog(false)}>
                Close
              </Button>
              <Button type="button" onClick={() => window.location.href = 'tel:+18005550199'}>
                Call Support
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

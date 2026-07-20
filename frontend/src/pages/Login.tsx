import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'
import Button from '../components/Button'
import TextField from '../components/TextField'
import { loginStart, loginVerify, loginStepUp } from '../api/auth'
import { getPasskey } from '../api/webauthn'
import { setAccessToken } from '../api/client'

const MailIcon = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" strokeWidth="1.7" />
    <path d="M4 7l8 6 8-6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
  </svg>
)

const PasskeyIcon = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <circle cx="9" cy="8" r="4" stroke="currentColor" strokeWidth="1.8" />
    <path
      d="M15 11a3 3 0 100-6M9 14c-3.3 0-6 2-6 4.5V21h9m6-7v7m0-7l2 1.5-2 1.5 2 1.5-2 1.5"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

export default function Login() {
  const location = useLocation()
  const [showLoggedOut, setShowLoggedOut] = useState(!!location.state?.loggedOut)
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Step-up (new-device) verification state
  const [stepUpHandle, setStepUpHandle] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const navigate = useNavigate()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email) return

    setLoading(true)
    setError(null)

    try {
      // 1. Start login assertion
      const startRes = await loginStart(email)

      // 2. Parse assertion options
      const parsedOptions = JSON.parse(startRes.assertionOptionsJson)

      // 3. Prompt user with passkey authenticator
      const credential = await getPasskey({ publicKey: parsedOptions.publicKey })

      // 4. Verify login signature
      const verifyRes = await loginVerify(startRes.handle, credential)

      // 5a. New device → require an emailed step-up code before issuing a session
      if (verifyRes.stepUpRequired && verifyRes.stepUpHandle) {
        setStepUpHandle(verifyRes.stepUpHandle)
        return
      }

      // 5b. Known device → store JWT and continue
      if (verifyRes.accessToken) {
        setAccessToken(verifyRes.accessToken)
        navigate('/dashboard')
      }
    } catch (err: any) {
      setError(err.message ?? 'Sign-in failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function handleStepUp(e: React.FormEvent) {
    e.preventDefault()
    if (!stepUpHandle || !code) return
    setLoading(true)
    setError(null)
    try {
      const res = await loginStepUp(stepUpHandle, code.trim())
      setAccessToken(res.accessToken)
      navigate('/dashboard')
    } catch (err: any) {
      setError(err.message ?? 'Verification failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  // ── Step-up (new device) screen ──────────────────────────────────────────
  if (stepUpHandle) {
    return (
      <AuthLayout
        title="Verify this device"
        subtitle="You're signing in from a device we haven't seen before. Enter the 6-digit code we just emailed you."
      >
        <div className="mb-5 flex items-start gap-3 rounded-xl border border-brand-100 bg-brand-50 p-3.5 text-sm text-brand-800">
          <svg className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
            <path d="M12 8v4m0 3h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <p>Your passkey checked out — this extra step confirms the new device is really you.</p>
        </div>

        <form onSubmit={handleStepUp} className="space-y-4">
          <TextField
            label="Verification code"
            id="code"
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            required
            placeholder="123456"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            disabled={loading}
            className="tracking-[0.4em] text-center text-lg"
          />

          {error && <Alert kind="error" message={error} />}

          <Button type="submit" loading={loading} disabled={code.length !== 6}>
            Verify &amp; sign in
          </Button>
        </form>

        <button
          onClick={() => {
            setStepUpHandle(null)
            setCode('')
            setError(null)
          }}
          className="mt-4 w-full text-center text-sm text-gray-400 hover:text-brand-600"
        >
          ← Back to sign in
        </button>
      </AuthLayout>
    )
  }

  // ── Sign-in screen ───────────────────────────────────────────────────────
  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in with your passkey — no password required."
      footer={
        <div className="space-y-3 text-center text-sm">
          <p className="text-gray-500">
            New to SecureBank?{' '}
            <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>
          <p className="text-gray-400">
            Lost your device?{' '}
            <Link to="/recover" className="font-medium text-gray-500 hover:text-brand-600">
              Recover account
            </Link>
          </p>
        </div>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <TextField
          label="Email address"
          id="email"
          name="email"
          type="email"
          required
          placeholder="you@example.com"
          autoComplete="username webauthn"
          value={email}
          onChange={(e) => {
            setEmail(e.target.value)
            setShowLoggedOut(false)
          }}
          disabled={loading}
          icon={MailIcon}
        />

        {showLoggedOut && <Alert kind="success" message="You have been signed out successfully." />}
        {error && <Alert kind="error" message={error} />}

        <Button type="submit" loading={loading} icon={PasskeyIcon}>
          Sign in with passkey
        </Button>
      </form>

      <div className="mt-5 flex items-center justify-center gap-2 text-xs text-gray-400">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="1.6" />
          <path d="M8 11V8a4 4 0 118 0v3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
        </svg>
        Your device verifies you with Face ID, fingerprint, or PIN.
      </div>
    </AuthLayout>
  )
}

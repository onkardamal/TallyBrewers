import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Button from '../components/Button'
import Alert from '../components/Alert'
import TextField from '../components/TextField'
import { resendVerification, verifyEmail } from '../api/auth'
import { ApiError } from '../api/client'
import { getPendingEmail } from '../api/pendingRegistration'

type Status = 'idle' | 'verifying' | 'verified' | 'error'

/**
 * Email verification screen with two modes:
 *  - With ?token=... in the URL (from the email link): auto-verify, then
 *    offer to continue to passkey setup.
 *  - Without a token: "check your inbox" state with a resend option.
 */
export default function VerifyEmail() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token')

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'idle')
  const [error, setError] = useState('')
  const [email, setEmail] = useState(getPendingEmail())
  const [resent, setResent] = useState(false)
  const [resending, setResending] = useState(false)
  const attempted = useRef(false)

  useEffect(() => {
    if (!token || attempted.current) {
      return
    }
    attempted.current = true
    verifyEmail(token)
      .then(() => setStatus('verified'))
      .catch((err) => {
        setStatus('error')
        setError(
          err instanceof ApiError
            ? err.message
            : 'Invalid or expired verification link.',
        )
      })
  }, [token])

  async function handleResend() {
    setError('')
    setResent(false)
    setResending(true)
    try {
      await resendVerification(email)
      setResent(true)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Could not resend. Please try again.',
      )
    } finally {
      setResending(false)
    }
  }

  if (status === 'verifying') {
    return (
      <AuthLayout title="Verifying your email">
        <p className="text-center text-sm text-gray-500">One moment…</p>
      </AuthLayout>
    )
  }

  if (status === 'verified') {
    return (
      <AuthLayout
        title="Email verified"
        subtitle="Next, set up a passkey to secure your account."
      >
        <Button onClick={() => navigate('/passkey-setup')}>
          Set up passkey
        </Button>
      </AuthLayout>
    )
  }

  if (status === 'error') {
    return (
      <AuthLayout title="Verification failed">
        <Alert kind="error" message={error} />
        <p className="mb-4 text-sm text-gray-500">
          The link may have expired or already been used. Enter your email to
          get a new link.
        </p>
        {resent && (
          <Alert kind="success" message="A new verification email has been sent." />
        )}
        <div className="space-y-4">
          <TextField
            label="Email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Button onClick={handleResend} loading={resending} variant="secondary">
            Resend verification email
          </Button>
        </div>
      </AuthLayout>
    )
  }

  // idle: "check your inbox"
  return (
    <AuthLayout
      title="Check your inbox"
      subtitle="We sent a verification link to your email. Open it to continue."
    >
      {error && <Alert kind="error" message={error} />}
      {resent && (
        <Alert kind="success" message="A new verification email has been sent." />
      )}
      <div className="space-y-4">
        <TextField
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Button onClick={handleResend} loading={resending} variant="secondary">
          Resend verification email
        </Button>
      </div>
    </AuthLayout>
  )
}

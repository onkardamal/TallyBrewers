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
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Mode 2: Verify and Register New Passkey
  const [recoveryCode, setRecoveryCode] = useState('')

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

  if (token) {
    // Mode 2: Verification and Passkey Reset form
    return (
      <AuthLayout title="Recover account" subtitle="Enter your email and one of your saved recovery codes to register a new passkey.">
        <form onSubmit={handleVerifyAndRecover} className="space-y-4">
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
    )
  }

  // Mode 1: Request Link form
  return (
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

      <p className="mt-4 text-center text-sm text-gray-500">
        Remembered your passkey?{' '}
        <Link to="/" className="font-medium text-gray-900 hover:underline">
          Go back to sign in
        </Link>
      </p>
    </AuthLayout>
  )
}

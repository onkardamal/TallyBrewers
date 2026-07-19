import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Button from '../components/Button'
import Alert from '../components/Alert'
import TextField from '../components/TextField'
import { passkeyRegisterFinish, passkeyRegisterStart } from '../api/auth'
import { ApiError } from '../api/client'
import { createPasskey, isWebAuthnSupported } from '../api/webauthn'
import { getPendingEmail } from '../api/pendingRegistration'
import SecurityWalkthrough from '../components/SecurityWalkthrough'

/**
 * Passkey setup screen. Runs the real WebAuthn registration ceremony:
 * start (server challenge) -> navigator.credentials.create() -> finish
 * (server verification). On success, routes to the recovery codes screen with
 * the one-time codes returned by the backend.
 */
export default function PasskeySetup() {
  const navigate = useNavigate()
  const [email, setEmail] = useState(getPendingEmail())
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const supported = isWebAuthnSupported()

  async function handleCreatePasskey() {
    setError('')
    setLoading(true)
    try {
      const start = await passkeyRegisterStart(email)
      const credential = await createPasskey(start.creationOptions)
      const result = await passkeyRegisterFinish(start.handle, credential)
      navigate('/recovery-codes', {
        state: { recoveryCodes: result.recoveryCodes },
      })
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else if (err instanceof DOMException && err.name === 'NotAllowedError') {
        setError('Passkey creation was cancelled or timed out.')
      } else if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Could not create a passkey. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout
      title="Set up your passkey"
      subtitle="Use your device's fingerprint, face, or security key. No passwords."
    >
      {!supported && (
        <Alert
          kind="error"
          message="This browser does not support passkeys. Please use an up-to-date browser."
        />
      )}
      {error && <Alert kind="error" message={error} />}
      <div className="mb-5">
        <SecurityWalkthrough />
      </div>
      <div className="space-y-4">
        <TextField
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Button
          onClick={handleCreatePasskey}
          loading={loading}
          disabled={!supported || !email}
        >
          Create passkey
        </Button>
      </div>
    </AuthLayout>
  )
}

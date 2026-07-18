import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Button from '../components/Button'
import Alert from '../components/Alert'
import { clearPendingEmail } from '../api/pendingRegistration'

interface RecoveryState {
  recoveryCodes?: string[]
}

/**
 * One-time display of recovery codes generated during first passkey
 * registration. The codes are only available in navigation state (never
 * refetchable) and are shown exactly once. The user must confirm they have
 * saved them before continuing.
 */
export default function RecoveryCodes() {
  const location = useLocation()
  const navigate = useNavigate()
  const codes = (location.state as RecoveryState | null)?.recoveryCodes ?? []
  const [acknowledged, setAcknowledged] = useState(false)

  if (codes.length === 0) {
    return (
      <AuthLayout title="Recovery codes">
        <Alert
          kind="info"
          message="Your recovery codes were shown once during setup and can't be displayed again."
        />
        <Button onClick={() => navigate('/')}>Go to sign in</Button>
      </AuthLayout>
    )
  }

  function handleContinue() {
    clearPendingEmail()
    navigate('/dashboard')
  }

  return (
    <AuthLayout
      title="Save your recovery codes"
      subtitle="Store these somewhere safe. They're shown only once and let you recover access if you lose your device."
    >
      <ul className="mb-4 grid grid-cols-2 gap-2 rounded-lg border border-gray-200 bg-gray-50 p-4 font-mono text-sm text-gray-800">
        {codes.map((code) => (
          <li key={code} className="text-center">
            {code}
          </li>
        ))}
      </ul>
      <label className="mb-4 flex items-start gap-2 text-left text-sm text-gray-600">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(e) => setAcknowledged(e.target.checked)}
          className="mt-0.5"
        />
        <span>I have saved my recovery codes in a safe place.</span>
      </label>
      <Button onClick={handleContinue} disabled={!acknowledged}>
        Continue
      </Button>
    </AuthLayout>
  )
}

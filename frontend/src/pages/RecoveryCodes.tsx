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
  const [copied, setCopied] = useState(false)

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

  function handleCopy() {
    navigator.clipboard.writeText(codes.join('\n')).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 3000)
    })
  }

  function handleDownload() {
    const element = document.createElement('a')
    const file = new Blob([codes.join('\n')], { type: 'text/plain' })
    element.href = URL.createObjectURL(file)
    element.download = 'securebank-recovery-codes.txt'
    document.body.appendChild(element)
    element.click()
    document.body.removeChild(element)
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
          <li key={code} className="text-center font-semibold tracking-wider">
            {code}
          </li>
        ))}
      </ul>

      <div className="mb-6 flex gap-3">
        <Button onClick={handleCopy} variant="secondary" className="flex-1 py-2 text-xs">
          {copied ? '✓ Copied' : 'Copy to Clipboard'}
        </Button>
        <Button onClick={handleDownload} variant="secondary" className="flex-1 py-2 text-xs">
          Download as .TXT
        </Button>
      </div>

      <label className="mb-6 flex items-start gap-2.5 text-left text-sm text-gray-600 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(e) => setAcknowledged(e.target.checked)}
          className="mt-1 h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-gray-900"
        />
        <span>I have securely saved my recovery codes in a safe place.</span>
      </label>
      <Button onClick={handleContinue} disabled={!acknowledged}>
        Continue to Dashboard
      </Button>
    </AuthLayout>
  )
}

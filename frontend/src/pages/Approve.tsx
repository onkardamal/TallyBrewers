import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'
import Button from '../components/Button'
import { getMe, qrApprove, qrApproveStart, type UserDto } from '../api/auth'
import { getAccessToken, tryRestoreSession } from '../api/client'
import { getPasskey } from '../api/webauthn'

type Phase = 'checking' | 'ready' | 'unauthenticated' | 'approved' | 'error'

/**
 * Phone side of cross-device login. The user must already be signed in here;
 * approving binds the desktop's pending QR link to this account. If the phone
 * has a valid refresh cookie, the authenticated calls succeed via silent
 * refresh even on a cold page load.
 */
export default function Approve() {
  const [params] = useSearchParams()
  const linkId = params.get('link')
  const [phase, setPhase] = useState<Phase>('checking')
  const [user, setUser] = useState<UserDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [approving, setApproving] = useState(false)

  useEffect(() => {
    if (!linkId) {
      setError('This sign-in link is invalid.')
      setPhase('error')
      return
    }
    ;(async () => {
      // Cold page load: restore the session from the refresh cookie before
      // calling an authenticated endpoint (protected routes return 403, not
      // 401, when no token is present, so auto-refresh-on-401 won't fire here).
      if (!getAccessToken()) {
        await tryRestoreSession()
      }
      try {
        const u = await getMe()
        setUser(u)
        setPhase('ready')
      } catch {
        setPhase('unauthenticated')
      }
    })()
  }, [linkId])

  async function handleApprove() {
    if (!linkId) return
    setError(null)
    setApproving(true)
    try {
      // Require a fresh passkey/biometric tap before approving.
      const start = await qrApproveStart()
      const options = JSON.parse(start.assertionOptionsJson)
      const credential = await getPasskey({ publicKey: options.publicKey })
      await qrApprove(linkId, start.handle, credential)
      setPhase('approved')
    } catch (err: any) {
      if (err instanceof DOMException && err.name === 'NotAllowedError') {
        setError('Passkey check was cancelled. Approval needs your fingerprint or Face ID.')
      } else {
        setError(err.message ?? 'Could not approve this sign-in.')
      }
    } finally {
      setApproving(false)
    }
  }

  if (phase === 'checking') {
    return (
      <AuthLayout title="Confirming…" subtitle="Checking your session.">
        <div className="flex justify-center py-4">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-gray-200 border-t-brand-600" />
        </div>
      </AuthLayout>
    )
  }

  if (phase === 'unauthenticated') {
    return (
      <AuthLayout
        title="Sign in first"
        subtitle="To approve a sign-in on another device, sign in on this device first."
      >
        <Link to="/">
          <Button>Go to sign in</Button>
        </Link>
      </AuthLayout>
    )
  }

  if (phase === 'approved') {
    return (
      <AuthLayout
        title="You're all set"
        subtitle="Your other device is now signed in. You can close this page."
      >
        <div className="flex flex-col items-center py-2">
          <div className="sb-pulse-ring flex h-16 w-16 items-center justify-center rounded-full bg-accent-500/12">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M20 6L9 17l-5-5" stroke="#059669" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>
      </AuthLayout>
    )
  }

  // ready
  return (
    <AuthLayout
      title="Approve sign-in?"
      subtitle="A device is trying to sign in to your SecureBank account using this phone."
    >
      <div className="mb-5 rounded-xl border border-gray-100 bg-gray-50/60 p-4 text-sm">
        <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Approving as</p>
        <p className="mt-1 font-medium text-brand-950">{user?.name}</p>
        <p className="text-gray-500">{user?.email}</p>
      </div>

      <div className="mb-5 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3.5 text-sm text-amber-800">
        <svg className="mt-0.5 h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 9v4m0 4h.01M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L14.7 3.9a2 2 0 00-3.4 0z" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <p>Only approve if you just started a sign-in on your own computer.</p>
      </div>

      {error && <Alert kind="error" message={error} />}

      <div className="space-y-3">
        <Button
          onClick={handleApprove}
          loading={approving}
          icon={
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 2l7 3v6c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V5l7-3z" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" />
              <path d="M9 12l2 2 4-4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          }
        >
          Approve with passkey
        </Button>
        <Link to="/">
          <Button variant="secondary">Not me — cancel</Button>
        </Link>
      </div>
    </AuthLayout>
  )
}

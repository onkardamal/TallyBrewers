import { useCallback, useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'
import Button from '../components/Button'
import { qrStart, qrStatus } from '../api/auth'
import { setAccessToken } from '../api/client'

type Phase = 'loading' | 'waiting' | 'expired' | 'error'

/**
 * Desktop side of cross-device login: shows a QR the user scans with a signed-in
 * phone, then polls until the phone approves and the session is issued here.
 */
export default function QrLogin() {
  const navigate = useNavigate()
  const [phase, setPhase] = useState<Phase>('loading')
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const pollRef = useRef<number | null>(null)

  const begin = useCallback(async () => {
    setPhase('loading')
    setError(null)
    if (pollRef.current) window.clearInterval(pollRef.current)

    try {
      const { linkId, desktopToken } = await qrStart()
      const approveUrl = `${window.location.origin}/approve?link=${encodeURIComponent(linkId)}`
      setQrDataUrl(
        await QRCode.toDataURL(approveUrl, {
          width: 240,
          margin: 1,
          color: { dark: '#131a44', light: '#ffffff' },
        }),
      )
      setPhase('waiting')

      // Poll for approval. The status endpoint is intentionally not rate-limited.
      pollRef.current = window.setInterval(async () => {
        try {
          const res = await qrStatus(linkId, desktopToken)
          if (res.status === 'APPROVED' && res.accessToken) {
            if (pollRef.current) window.clearInterval(pollRef.current)
            setAccessToken(res.accessToken)
            navigate('/dashboard')
          } else if (res.status === 'EXPIRED' || res.status === 'CONSUMED') {
            if (pollRef.current) window.clearInterval(pollRef.current)
            setPhase('expired')
          }
        } catch {
          // Transient poll error — keep trying until the link expires.
        }
      }, 1500)
    } catch (err: any) {
      setError(err.message ?? 'Could not start a QR sign-in.')
      setPhase('error')
    }
  }, [navigate])

  useEffect(() => {
    begin()
    return () => {
      if (pollRef.current) window.clearInterval(pollRef.current)
    }
  }, [begin])

  return (
    <AuthLayout
      title="Sign in with your phone"
      subtitle="Scan this code with a device where you're already signed in to SecureBank."
      footer={
        <p className="text-center text-sm text-gray-500">
          <Link to="/" className="font-semibold text-brand-600 hover:text-brand-700">
            ← Use email &amp; passkey instead
          </Link>
        </p>
      }
    >
      <div className="flex flex-col items-center">
        <div className="flex h-[272px] w-[272px] items-center justify-center rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          {phase === 'waiting' && qrDataUrl ? (
            <img src={qrDataUrl} alt="QR code to sign in" className="h-full w-full" />
          ) : phase === 'expired' ? (
            <div className="text-center">
              <p className="text-sm font-medium text-brand-900">This code expired</p>
              <p className="mt-1 text-xs text-gray-500">Generate a fresh one to continue.</p>
              <div className="mt-4">
                <Button onClick={begin} variant="secondary">
                  New code
                </Button>
              </div>
            </div>
          ) : (
            <div className="h-10 w-10 animate-spin rounded-full border-2 border-gray-200 border-t-brand-600" />
          )}
        </div>

        {phase === 'waiting' && (
          <span className="mt-4 inline-flex items-center gap-2 rounded-full bg-accent-500/12 px-3 py-1 text-xs font-medium text-accent-700">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent-500" />
            Waiting for approval…
          </span>
        )}

        {error && (
          <div className="mt-6 w-full">
            <Alert kind="error" message={error} />
          </div>
        )}

        <ol className="mt-8 w-full space-y-2.5 text-sm text-gray-500">
          {[
            'Open SecureBank on your signed-in phone',
            'Scan this QR code with the camera',
            'Approve the sign-in on your phone',
          ].map((step, i) => (
            <li key={i} className="flex items-center gap-3">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">
                {i + 1}
              </span>
              {step}
            </li>
          ))}
        </ol>
      </div>
    </AuthLayout>
  )
}

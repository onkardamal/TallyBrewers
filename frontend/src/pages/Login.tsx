import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'
import Button from '../components/Button'
import { loginStart, loginVerify } from '../api/auth'
import { getPasskey } from '../api/webauthn'
import { setAccessToken } from '../api/client'

export default function Login() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
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
      
      // 5. Store JWT and navigate to dashboard
      setAccessToken(verifyRes.accessToken)
      navigate('/dashboard')
    } catch (err: any) {
      setError(err.message ?? 'Sign-in failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="Sign in" subtitle="Passwordless banking, secured by passkeys.">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="email" className="sr-only">
            Email address
          </label>
          <input
            id="email"
            name="email"
            type="email"
            required
            placeholder="Email address"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={loading}
            className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:border-gray-900 focus:outline-none focus:ring-1 focus:ring-gray-900 disabled:opacity-50"
          />
        </div>

        {error && <Alert kind="error" message={error} />}

        <Button type="submit" loading={loading}>
          Sign in
        </Button>
      </form>

      <div className="mt-4 flex flex-col items-center gap-2 text-sm">
        <p className="text-gray-500">
          New to SecureBank?{' '}
          <Link to="/register" className="font-medium text-gray-900 hover:underline">
            Create an account
          </Link>
        </p>
        <p className="text-gray-500">
          Lost your device?{' '}
          <Link to="/recover" className="font-medium text-gray-900 hover:underline">
            Recover account
          </Link>
        </p>
      </div>
    </AuthLayout>
  )
}

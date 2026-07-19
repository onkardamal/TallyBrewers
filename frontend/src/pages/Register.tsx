import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import TextField from '../components/TextField'
import Button from '../components/Button'
import Alert from '../components/Alert'
import { register } from '../api/auth'
import { ApiError } from '../api/client'
import { setPendingEmail } from '../api/pendingRegistration'

/**
 * Registration screen: name, email, optional phone. On success the backend
 * sends a verification email; we route the user to the "check your inbox"
 * screen. Max two actions (submit, or go to login), per UI_GUIDELINES.
 */
export default function Register() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register({ name, email, phone: phone || undefined })
      setPendingEmail(email)
      navigate('/verify-email')
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Something went wrong. Please try again.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Passwordless. Secure by design.">
      {error && <Alert kind="error" message={error} />}
      <form onSubmit={handleSubmit} className="space-y-4">
        <TextField
          label="Full name"
          name="name"
          autoComplete="name"
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={loading}
        />
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={loading}
        />
        <TextField
          label="Phone (optional)"
          name="phone"
          type="tel"
          autoComplete="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          disabled={loading}
        />
        <Button type="submit" loading={loading}>
          Create account
        </Button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-500">
        Already have an account?{' '}
        <Link to="/" className="font-medium text-gray-900 hover:underline">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}

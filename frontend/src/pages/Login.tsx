import { Link } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'

/**
 * Login screen — the first screen (route "/"), per UI_GUIDELINES.
 *
 * Passwordless login (WebAuthn assertion) is implemented in Phase 3. For now
 * this screen routes new users to registration; the sign-in action will be
 * wired up when the login endpoints exist.
 */
export default function Login() {
  return (
    <AuthLayout title="Sign in" subtitle="Passwordless banking, secured by passkeys.">
      <Alert kind="info" message="Passkey sign-in is coming in the next phase." />
      <p className="text-center text-sm text-gray-500">
        New to SecureBank?{' '}
        <Link to="/register" className="font-medium text-gray-900 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  )
}

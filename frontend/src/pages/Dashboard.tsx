import AuthLayout from '../components/AuthLayout'
import Alert from '../components/Alert'

/**
 * Minimal authenticated landing page.
 *
 * This is NOT a banking dashboard. Per Project Brief.md, this project does not
 * build banking features, an admin console, or a security dashboard. This page
 * exists only to confirm the onboarding flow completed. A real authenticated
 * session (and logout) arrives with Phase 3.
 */
export default function Dashboard() {
  return (
    <AuthLayout title="You're all set" subtitle="Your SecureBank account is ready.">
      <Alert
        kind="success"
        message="Account created and passkey registered. Sign-in with your passkey arrives in the next phase."
      />
    </AuthLayout>
  )
}

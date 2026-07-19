import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AuthLayout from '../components/AuthLayout'
import Button from '../components/Button'
import { getMe, logout } from '../api/auth'
import type { UserDto } from '../api/auth'
import { setAccessToken } from '../api/client'

export default function Dashboard() {
  const [user, setUser] = useState<UserDto | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    getMe()
      .then((data) => {
        setUser(data)
        setLoading(false)
      })
      .catch((err) => {
        setError(err.message)
        setLoading(false)
        navigate('/')
      })
  }, [navigate])

  async function handleLogout() {
    try {
      await logout()
    } catch {
      // Ignore network errors on logout
    } finally {
      setAccessToken(null)
      navigate('/')
    }
  }

  if (loading) {
    return (
      <AuthLayout title="Loading..." subtitle="Fetching your account details.">
        <p className="text-center text-sm text-gray-500">Please wait.</p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title={`Welcome back, ${user?.name}`} subtitle="Your account dashboard is secure.">
      <div className="mb-6 space-y-2 rounded-lg border border-gray-200 bg-gray-50 p-4 text-left text-sm text-gray-700">
        <div>
          <strong className="block text-gray-500 font-normal">Email</strong>
          <span className="font-medium text-gray-900">{user?.email}</span>
        </div>
        {user?.phone && (
          <div className="mt-2">
            <strong className="block text-gray-500 font-normal">Phone</strong>
            <span className="font-medium text-gray-900">{user?.phone}</span>
          </div>
        )}
      </div>
      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
      <Button onClick={handleLogout} variant="secondary">
        Sign out
      </Button>
    </AuthLayout>
  )
}

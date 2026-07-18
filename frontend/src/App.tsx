import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import VerifyEmail from './pages/VerifyEmail'
import PasskeySetup from './pages/PasskeySetup'
import RecoveryCodes from './pages/RecoveryCodes'
import Recover from './pages/Recover'
import Dashboard from './pages/Dashboard'

/**
 * Route structure follows the authentication flow documented in
 * DATA_FLOW.md and Project Brief.md:
 *
 *   Registration -> Email Verification -> Passkey Registration
 *   -> Recovery Code Generation -> Login -> Dashboard
 *
 * Login ("/") is the first screen, per UI_GUIDELINES.md.
 * No authentication logic is wired up yet — Phase 1 provides structure only.
 */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/passkey-setup" element={<PasskeySetup />} />
        <Route path="/recovery-codes" element={<RecoveryCodes />} />
        <Route path="/recover" element={<Recover />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App

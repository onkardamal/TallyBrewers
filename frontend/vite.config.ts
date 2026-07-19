import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Defensive HTTP response headers for the SPA. These are safe in Vite dev
// (no strict CSP here, which would break HMR — CSP belongs on the production
// host serving the built assets). They mirror the backend's hardening.
const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'geolocation=(), camera=(), microphone=(), payment=()',
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    headers: securityHeaders,
  },
  preview: {
    port: 4173,
    headers: securityHeaders,
  },
})

/**
 * Best-effort, dependency-free parsing of a User-Agent string into a friendly
 * device label. Intentionally coarse — enough to help a user recognise "my
 * MacBook in Chrome" vs "an unknown Android device", not full UA analytics.
 */
export interface DeviceInfo {
  browser: string
  os: string
  kind: 'desktop' | 'mobile' | 'tablet' | 'unknown'
  label: string
}

export function parseUserAgent(ua?: string): DeviceInfo {
  if (!ua) {
    return { browser: 'Unknown', os: 'Unknown device', kind: 'unknown', label: 'Unknown device' }
  }

  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /OPR\/|Opera/.test(ua)
      ? 'Opera'
      : /Chrome\//.test(ua) && !/Chromium/.test(ua)
        ? 'Chrome'
        : /Firefox\//.test(ua)
          ? 'Firefox'
          : /Safari\//.test(ua) && /Version\//.test(ua)
            ? 'Safari'
            : 'Browser'

  let os = 'Unknown'
  let kind: DeviceInfo['kind'] = 'unknown'
  if (/iPhone/.test(ua)) {
    os = 'iPhone'
    kind = 'mobile'
  } else if (/iPad/.test(ua)) {
    os = 'iPad'
    kind = 'tablet'
  } else if (/Android/.test(ua)) {
    os = 'Android'
    kind = /Mobile/.test(ua) ? 'mobile' : 'tablet'
  } else if (/Mac OS X|Macintosh/.test(ua)) {
    os = 'macOS'
    kind = 'desktop'
  } else if (/Windows/.test(ua)) {
    os = 'Windows'
    kind = 'desktop'
  } else if (/Linux/.test(ua)) {
    os = 'Linux'
    kind = 'desktop'
  }

  return { browser, os, kind, label: `${browser} on ${os}` }
}

/** Compact "3 minutes ago" style relative time from an ISO timestamp. */
export function relativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  const diffMs = Date.now() - then
  const sec = Math.round(diffMs / 1000)
  if (sec < 45) return 'just now'
  const min = Math.round(sec / 60)
  if (min < 60) return `${min} min ago`
  const hr = Math.round(min / 60)
  if (hr < 24) return `${hr} hr ago`
  const day = Math.round(hr / 24)
  if (day < 30) return `${day} day${day === 1 ? '' : 's'} ago`
  return new Date(iso).toLocaleDateString()
}

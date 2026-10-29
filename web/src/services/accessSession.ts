import type { Tokens } from '../types/api'

/** UI restoration hint only. The server still verifies signature, expiry and revocation. */
export function readAccessSession(token: string): { accountId: string; expiresAt: number } | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3 || !parts.every(Boolean)) return null
    const payload = parts[1]!.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(globalThis.atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')))
    const accountId = String(claims.uid ?? claims.sub ?? '')
    if (!/^[1-9]\d*$/.test(accountId) || (claims.sub != null && String(claims.sub) !== accountId) ||
        !Number.isSafeInteger(claims.exp) || claims.exp <= 0 || !Number.isSafeInteger(claims.exp * 1000)) return null
    return { accountId, expiresAt: claims.exp * 1000 }
  } catch { return null }
}
export function hasUsableAccessToken(token: string, now = Date.now()): boolean {
  const session = readAccessSession(token)
  return Boolean(session && session.expiresAt > now)
}
export function persistAccessSession(tokens: Tokens) {
  try {
    sessionStorage.setItem('charroom_token', tokens.accessToken)
    const session = readAccessSession(tokens.accessToken)
    if (session) sessionStorage.setItem('charroom_tokenExpiresAt', String(session.expiresAt))
    else sessionStorage.removeItem('charroom_tokenExpiresAt')
    localStorage.setItem('charroom_refreshToken', tokens.refreshToken)
  } catch { /* In-memory session remains usable if browser storage is disabled. */ }
}

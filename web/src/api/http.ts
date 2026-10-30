import store from '../store'
import type { ApiBody, FetchResult, Tokens } from '../types/api'
import { sessionEpoch } from '../services/sessionEpoch'
import { requestScope, abortable } from '../utils/requestScope'
import { persistAccessSession, readAccessSession } from '../services/accessSession'

export const API_BASE = import.meta.env.VITE_API_BASE || 'https://www.chatlite.xin/api'
type RefreshReply = { state: 'ready'; tokens: Tokens } | { state: 'invalid' | 'unavailable' }
export type SessionRecovery = 'ready' | 'invalid' | 'unavailable' | 'stale'
let refreshing: { version: number; promise: Promise<SessionRecovery> } | null = null

async function parseResponse<T>(response: Response): Promise<FetchResult<T>> {
  const text = await response.text()
  let value: unknown
  try { value = JSON.parse(text) } catch { value = { message: text } }
  const wrapped = value !== null && typeof value === 'object' && !Array.isArray(value) &&
    ('data' in value || 'code' in value || 'message' in value)
  const body = (wrapped ? value : { data: value }) as ApiBody<T>
  const retryAfter = response.headers.get('Retry-After')
  const retryAfterSeconds = retryAfter && /^\d+$/.test(retryAfter) ? Number(retryAfter) : undefined
  return { ok: response.ok, status: response.status, body,
    ...(retryAfterSeconds != null && Number.isFinite(retryAfterSeconds) ? { retryAfterSeconds } : {}) }
}
async function requestRefresh(token: string, signal: AbortSignal): Promise<RefreshReply> {
  if (!token) return { state: 'invalid' }
  const scope = requestScope([signal])
  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: token }), signal: scope.signal
    })
    const result = await parseResponse<Partial<Tokens>>(response)
    if (result.status === 401 || result.status === 403 || [401, 1002, 1006, 2001].includes(result.body?.code ?? -1)) return { state: 'invalid' }
    if (!result.ok || (result.body?.code != null && result.body.code !== 0) || !result.body?.data?.accessToken) return { state: 'unavailable' }
    return { state: 'ready', tokens: { accessToken: String(result.body.data.accessToken), refreshToken: String(result.body.data.refreshToken || '') } }
  } catch { return { state: 'unavailable' } }
  finally { scope.close() }
}
export async function refreshToken(token: string, signal = sessionEpoch.capture().signal): Promise<Tokens | null> {
  const reply = await requestRefresh(token, signal)
  return reply.state === 'ready' ? reply.tokens : null
}
function clearAuth() {
  store.clearAll()
  store.setToken('')
  store.setRefreshToken('')
  store.setAccountId('')
  store.setLoginValid(false)
  sessionStorage.removeItem('charroom_token')
  sessionStorage.removeItem('charroom_tokenExpiresAt')
  localStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_refreshToken')
  localStorage.removeItem('charroom_accountId')
  window.location.href = '/app'
}
/** One rotation for concurrent REST failures and realtime authentication rejection. */
export async function refreshSession(rejectedToken = store.state.token): Promise<SessionRecovery> {
  const session = sessionEpoch.capture(), owner = String(store.state.accountId)
  const current = () => session.isCurrent() && String(store.state.accountId) === owner
  if (store.state.token && store.state.token !== rejectedToken) return 'ready'
  if (!refreshing || refreshing.version !== session.version) {
    const promise = requestRefresh(store.state.refreshToken, session.signal).then((reply): SessionRecovery => {
      if (!current()) return 'stale'
      if (reply.state === 'invalid') { clearAuth(); return 'invalid' }
      if (reply.state !== 'ready') return reply.state
      const identity = readAccessSession(reply.tokens.accessToken)
      if (identity && owner && identity.accountId !== owner) { clearAuth(); return 'invalid' }
      store.setToken(reply.tokens.accessToken)
      store.setRefreshToken(reply.tokens.refreshToken)
      persistAccessSession(reply.tokens)
      return 'ready'
    })
    const flight = { version: session.version, promise }
    refreshing = flight
    void flight.promise.finally(() => { if (refreshing === flight) refreshing = null })
  }
  return refreshing.promise
}
function unauthorized(result: FetchResult<unknown>) {
  return result.status === 401 || [401, 1002, 1006].includes(result.body?.code ?? -1)
}
export async function safeFetch<T = unknown>(url: string, options: RequestInit = {}): Promise<FetchResult<T>> {
  const session = sessionEpoch.capture(), owner = String(store.state.accountId)
  const scope = requestScope([session.signal, options.signal])
  const current = () => session.isCurrent() && String(store.state.accountId) === owner && !scope.signal.aborted
  const cancelled: FetchResult<T> = { ok: false, status: 0, body: null }
  try {
    const headers = new Headers(options.headers)
    if (store.state.token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${store.state.token}`)
    const rejectedToken = store.state.token
    let result = await parseResponse<T>(await fetch(url, { ...options, headers, signal: scope.signal }))
    if (!current()) return cancelled
    if (unauthorized(result) && rejectedToken) {
      const recovered = await abortable(refreshSession(rejectedToken), scope.signal)
      if (recovered === 'invalid') return { ok: false, status: 401, body: null }
      if (!current()) return cancelled
      if (recovered !== 'ready') return { ok: false, status: 0, body: null }
      headers.set('Authorization', `Bearer ${store.state.token}`)
      result = await parseResponse<T>(await fetch(url, { ...options, headers, signal: scope.signal }))
      if (current() && unauthorized(result)) { clearAuth(); return { ok: false, status: 401, body: null } }
    }
    return current() ? result : cancelled
  } catch { return { ok: false, status: 0, body: null } }
  finally { scope.close() }
}

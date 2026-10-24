import store from '../store'
import type { ApiBody, FetchResult, Tokens } from '../types/api'

export const API_BASE = import.meta.env.VITE_API_BASE || 'https://chatlite.xin/api'
let refreshing: Promise<Tokens | null> | null = null

async function parseResponse<T>(response: Response): Promise<FetchResult<T>> {
  const text = await response.text()
  let value: unknown
  try { value = JSON.parse(text) } catch { value = { message: text } }
  const wrapped = value !== null && typeof value === 'object' && !Array.isArray(value) &&
    ('data' in value || 'code' in value || 'message' in value)
  const body = (wrapped ? value : { data: value }) as ApiBody<T>
  return { ok: response.ok, status: response.status, body }
}
export async function refreshToken(token: string): Promise<Tokens | null> {
  if (!token) return null
  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: token })
    })
    const result = await parseResponse<Partial<Tokens>>(response)
    if (!result.ok || !result.body?.data?.accessToken) return null
    return { accessToken: String(result.body.data.accessToken), refreshToken: String(result.body.data.refreshToken || '') }
  } catch { return null }
}
function clearAuth() {
  store.clearAll()
  store.setToken('')
  store.setRefreshToken('')
  store.setAccountId('')
  store.setLoginValid(false)
  sessionStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_refreshToken')
  localStorage.removeItem('charroom_accountId')
  window.location.href = '/app'
}
export async function safeFetch<T = unknown>(url: string, options: RequestInit = {}): Promise<FetchResult<T>> {
  try {
    const headers = new Headers(options.headers)
    if (store.state.token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${store.state.token}`)
    let response = await fetch(url, { ...options, headers })
    if (response.status === 401 && store.state.refreshToken) {
      // All concurrent requests settle, including a failed refresh. The refresh
      // endpoint uses fetch directly so it cannot recursively wait on itself.
      refreshing ??= refreshToken(store.state.refreshToken).finally(() => { refreshing = null })
      const tokens = await refreshing
      if (!tokens) { clearAuth(); return { ok: false, status: 401, body: null } }
      store.setToken(tokens.accessToken)
      store.setRefreshToken(tokens.refreshToken)
      sessionStorage.setItem('charroom_token', tokens.accessToken)
      localStorage.setItem('charroom_refreshToken', tokens.refreshToken)
      headers.set('Authorization', `Bearer ${tokens.accessToken}`)
      response = await fetch(url, { ...options, headers })
    }
    return await parseResponse<T>(response)
  } catch { return { ok: false, status: 0, body: null } }
}

import { getDeviceId, getDeviceType } from '../utils/device'
import { requestScope } from '../utils/requestScope'

const BASE = import.meta.env.VITE_API_BASE || 'https://chatlite.xin/api'
const ACCESS = 'charroom_admin_access', REFRESH = 'charroom_admin_refresh'
export const releasePlatforms = ['android', 'desktop'] as const
export type ReleasePlatform = typeof releasePlatforms[number]
export interface ReleaseInput {
  platform: ReleasePlatform; version: string; minSupportedVersion: string
  forceUpdate: boolean; downloadUrl: string; releaseNotes: string
  packageType: string; architecture: string; fileSize: number; sha256: string; versionCode: number
}
export interface AppRelease extends ReleaseInput {
  id: string; state: 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'; revision: number
  createdBy: number; updatedBy: number; createdAt: string; updatedAt: string; publishedAt: string | null
}
export interface ReleasePage { items: AppRelease[]; hasMore: boolean; active: AppRelease | null }
export interface ReleaseAudit { id: string; releaseId: string; actorId: number; action: string; revision: number; createdAt: string }
interface Tokens { accessToken: string; refreshToken: string }
export class AdminApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message) }
}

/** Admin credentials and cancellation are independent of the chat session. */
export class AdminApi {
  private access = sessionStorage.getItem(ACCESS) || ''
  private refresh = sessionStorage.getItem(REFRESH) || ''
  private generation = 0
  private controller = new AbortController()
  private refreshing: Promise<void> | null = null
  get hasSession() { return !!this.access }
  private install(tokens: Tokens) {
    this.access = tokens.accessToken; this.refresh = tokens.refreshToken
    sessionStorage.setItem(ACCESS, this.access); sessionStorage.setItem(REFRESH, this.refresh)
  }
  logout() {
    this.generation++; this.controller.abort(); this.controller = new AbortController(); this.refreshing = null
    this.access = ''; this.refresh = ''; sessionStorage.removeItem(ACCESS); sessionStorage.removeItem(REFRESH)
  }
  private async fetchBody<T>(path: string, options: RequestInit = {}, authenticated = true): Promise<T> {
    const generation = this.generation
    const scope = requestScope([this.controller.signal, options.signal])
    try {
      const response = await fetch(`${BASE}${path}`, {
        ...options, cache: 'no-store', signal: scope.signal,
        headers: { 'Content-Type': 'application/json', ...(authenticated ? { Authorization: `Bearer ${this.access}` } : {}) }
      })
      const body = await response.json() as { code: number; message?: string; data?: T }
      if (generation !== this.generation) throw new AdminApiError(0, 'Session changed')
      if (!response.ok || body.code !== 0) throw new AdminApiError(response.status, body.message || `HTTP ${response.status}`)
      return body.data as T
    } finally { scope.close() }
  }
  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const generation = this.generation
    try { return await this.fetchBody<T>(path, options) }
    catch (error) {
      if (!(error instanceof AdminApiError) || error.status !== 401 || !this.refresh || generation !== this.generation) throw error
      if (!this.refreshing) {
        const flight = this.fetchBody<Tokens>('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: this.refresh }) }, false)
          .then(tokens => { if (generation !== this.generation || !tokens.accessToken) throw new AdminApiError(401, 'Invalid session'); this.install(tokens) })
        this.refreshing = flight
        void flight.finally(() => { if (this.refreshing === flight) this.refreshing = null }).catch(() => {})
      }
      try { await this.refreshing }
      catch (error) { if (generation === this.generation) this.logout(); throw error }
      if (generation !== this.generation) throw new AdminApiError(0, 'Session changed')
      return this.fetchBody<T>(path, options)
    }
  }
  async login(account: string, password: string) {
    this.logout()
    const generation = this.generation
    const tokens = await this.fetchBody<Tokens>('/auth/login', { method: 'POST', body: JSON.stringify({
      account, password, deviceType: getDeviceType(), deviceId: `${getDeviceId()}-admin`
    }) }, false)
    if (generation !== this.generation || !tokens.accessToken) throw new AdminApiError(401, 'Invalid session')
    this.install(tokens)
    try { return await this.me() } catch (error) { if (generation === this.generation) this.logout(); throw error }
  }
  me() { return this.request<{ userId: number }>('/admin/me') }
  list(platform: ReleasePlatform, page: number) { return this.request<ReleasePage>(`/admin/releases?platform=${platform}&page=${page}`) }
  create(input: ReleaseInput) { return this.request<AppRelease>('/admin/releases', { method: 'POST', body: JSON.stringify(input) }) }
  edit(row: AppRelease, input: ReleaseInput) {
    return this.request<AppRelease>(`/admin/releases/${encodeURIComponent(row.id)}?revision=${row.revision}`, { method: 'PUT', body: JSON.stringify(input) })
  }
  transition(row: AppRelease, action: 'publish' | 'withdraw') {
    return this.request<AppRelease>(`/admin/releases/${encodeURIComponent(row.id)}/${action}`, { method: 'POST', body: JSON.stringify({ revision: row.revision }) })
  }
  audits(id: string) { return this.request<ReleaseAudit[]>(`/admin/releases/${encodeURIComponent(id)}/audits`) }
}

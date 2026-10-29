import store from '../store'
import { getDeviceId, getDeviceType, DEVICE_TYPE } from '../utils/device'
import { safeFetch, API_BASE, refreshToken } from './http'
import { getTokenQuota, getTokenPrices, purchaseTokens, getPurchaseStatus } from './quota'
import * as copilotApi from './copilot'
import { sessionEpoch } from '../services/sessionEpoch'
import type { Id, User, Group } from '../types/chat'
import type { Tokens, LoginResult, OfflineMessage, SyncResult, RecentHistoryRequest, RecentHistoryPage, FriendRequest, FriendNotification, UserProfile, ApiBody } from '../types/api'
export { safeFetch, API_BASE, refreshToken }

/**
 * 后端业务失败一律是 HTTP 200 + `code != 0`（ApiResponse.failure）。
 * 只看 response.ok 会把「邮箱已注册」「验证码错误」当成成功，必须同时看 code。
 */
function businessError<T>(ok: boolean, body: ApiBody<T> | null | undefined, fallback: string) {
  if (!ok) return body?.message || fallback
  const code = body?.code
  if (code != null && code !== 0) return body?.message || fallback
  return null
}

export async function login(account: string, password: string): Promise<LoginResult> {
  if (!account || !password) return { ok: false, error: '请输入账号和密码' }
  const { ok, body } = await safeFetch<Tokens | string>(`${API_BASE}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password, deviceType: getDeviceType(), deviceId: getDeviceId() })
  })
  const loginError = businessError(ok, body as ApiBody<Tokens | string> | null, '登录失败')
  if (loginError) {
    return { ok: false, error: loginError }
  }

  const data = body?.data
  // 兼容新后端：data = { accessToken, refreshToken }
  if (data && typeof data === 'object') {
    const accessToken = String(data.accessToken || '')
    const refreshToken = String(data.refreshToken || '')
    if (accessToken) {
      return { ok: true, accessToken, refreshToken }
    }
  }
  // 兼容旧后端：data 直接是 access token
  if (typeof data === 'string' && data) {
    return { ok: true, accessToken: data, refreshToken: '' }
  }
  return { ok: false, error: '登录失败：服务器返回数据异常' }
}

export async function register(username: string, password: string) {
  if (!username || !password) return -1
  // 注册使用后端的 /user/register，传入 User 对象。只提供 username 和 password，email/phone 可留空。
  const userObj = { username, password, email: '', phone: '' }
  const { ok, body } = await safeFetch<number>(`${API_BASE}/auth/register/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(userObj)
  })
  if (businessError(ok, body, '注册失败')) return -1
  return body?.data ?? -1
}

export async function sendVerifyCode(email: string) {
  if (!email) return { ok: false, error: '请输入邮箱地址' }
  const { ok, body } = await safeFetch<unknown>(`${API_BASE}/auth/verify-code`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
  })
  const error = businessError(ok, body, '验证码发送失败')
  if (error) return { ok: false, error }
  return { ok: true }
}

export async function verifyRegister(email: string, code: string, password: string) {
  if (!email || !code || !password) return { ok: false, error: '请填写完整信息' }
  const { ok, body } = await safeFetch<User>(`${API_BASE}/auth/register/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, code, password })
  })
  const error = businessError(ok, body, '注册失败')
  if (error) return { ok: false, error }
  return { ok: true, data: body?.data }
}

/** Account-authorized seven-day history, independent of delivery flags and device cursors. */
export async function getRecentMessages(request: RecentHistoryRequest = {}): Promise<RecentHistoryPage> {
  const { ok, body } = await safeFetch<RecentHistoryPage>(`${API_BASE}/messages/recent`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
  })
  if (!ok || !body?.data) throw new Error('Recent history synchronization failed')
  return body.data
}

export async function getOfflineMessages(pageSize?: number) {
  const params = pageSize ? `?pageSize=${pageSize}` : ''
  const { ok, body } = await safeFetch<OfflineMessage[]>(`${API_BASE}/messages/offline${params}`, { method: 'GET' })
  if (!ok || body?.code !== 0 || !body.data) throw new Error('Offline history request failed')
  return body.data
}

/** Call only after the account's IndexedDB transaction has committed. */
export async function confirmOfflineMessages(ids: string[]) {
  const session = sessionEpoch.capture()
  for (let offset = 0; offset < ids.length; offset += 200) {
    if (!session.isCurrent()) throw new Error('Offline receipt session changed')
    const { ok, body } = await safeFetch<null>(`${API_BASE}/messages/offline/ack`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids: ids.slice(offset, offset + 200) })
    })
    if (!ok || body?.code !== 0) throw new Error('Offline history receipt failed')
  }
}

/**
 * 增量同步消息（基于 seqId 游标）。
 * 使用 per-device cursor，确保每个设备独立追踪已读位置
 */
export async function syncMessages(conversationId: string, lastSeqId: Id, limit = 50) {
  const { ok, body } = await safeFetch<SyncResult>(`${API_BASE}/sync/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, lastSeqId, limit, deviceType: DEVICE_TYPE })
  })
  if (!ok || !body?.data) return { messages: [], nextSeqId: lastSeqId, hasMore: false }
  return body.data
}

export async function getMyGroups() {
  const { ok, body } = await safeFetch<Group[]>(`${API_BASE}/groups`, { method: 'GET' })
  if (!ok) return []
  return body?.data || []
}

export interface UserSearchHit {
  id: string
  username: string
  avatarUrl?: string
  avatarKey?: string
  signature?: string
  relation: 'NONE' | 'PENDING' | 'FRIEND'
}

/** 按昵称/ID 搜人（加好友用）。发现页只含公开名片，这里覆盖全部用户。 */
export async function searchUsers(q: string, signal?: AbortSignal): Promise<UserSearchHit[]> {
  const keyword = q.trim()
  if (!keyword) return []
  const { ok, body } = await safeFetch<UserSearchHit[]>(`${API_BASE}/users/search?q=${encodeURIComponent(keyword)}`, { method: 'GET', signal })
  const error = businessError(ok, body, '搜索失败')
  if (error) throw new Error(error)
  return body?.data || []
}

export async function addFriend(account: string): Promise<{ ok: boolean; message: string }> {
  const { ok, body } = await safeFetch(`${API_BASE}/friends`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ account })
  })
  const error = businessError(ok, body, '请求失败')
  if (error) return { ok: false, message: error }
  return { ok: true, message: '' }
}

export async function getFriendRequests() {
  const { ok, body } = await safeFetch<FriendRequest[]>(`${API_BASE}/friends/requests`, { method: 'GET' })
  if (!ok) return []
  // 支持 ApiResponse 包装或直接返回数组
  return body?.data || []
}

export async function acceptFriend(requesterId: Id) {
  const friendId = Number(requesterId)
  const body = JSON.stringify({ friendId })
  const { ok, body: resp } = await safeFetch<User>(`${API_BASE}/friends/accept`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body
  })
  if(ok && resp?.data)window.dispatchEvent(new Event('contacts-changed'))
  // 返回新好友的用户信息（增量更新用）
  return ok ? (resp?.data || null) : null
}

export async function rejectFriend(requesterId: Id) {
  const friendId = Number(requesterId)
  const body = JSON.stringify({ friendId })
  const { ok } = await safeFetch(`${API_BASE}/friends/reject`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body
  })
  return ok
}

/**
 * 获取未读好友通知（登录时调用）
 */
export async function getFriendNotifications() {
  const { ok, body } = await safeFetch<FriendNotification[]>(`${API_BASE}/friends/notifications`, { method: 'GET' })
  if (!ok) return []
  return body?.data || []
}

/**
 * 标记好友通知已读
 */
export async function markFriendNotificationsRead() {
  const { ok } = await safeFetch(`${API_BASE}/friends/notifications/read`, { method: 'POST' })
  return ok
}

export async function addGroup(groupId: Id) {
  const { ok } = await safeFetch(`${API_BASE}/groups/${groupId}/join`, { method: 'POST' })
  if(ok)window.dispatchEvent(new Event('contacts-changed'))
  return ok
}

export async function getUserDetail(id: Id) {
  const { ok, body } = await safeFetch<User>(`${API_BASE}/friends/${id}`, { method: 'GET' })
  if (!ok) return null
  return body?.data || null
}

export async function delFriend(friendId: Id) {
  const { ok } = await safeFetch(`${API_BASE}/friends/${friendId}`, { method: 'DELETE' })
  if(ok)window.dispatchEvent(new Event('contacts-changed'))
  return ok
}

export async function getCurrentUser() {
  const { ok, body } = await safeFetch<User>(`${API_BASE}/users/me`, { method: 'GET' })
  if (!ok) return null
  return body?.data || null
}

/** 更新当前用户资料（昵称/签名/密码）。 */
export async function updateUserProfile({ username, phone, signature, password }: UserProfile = {}) {
  const body: UserProfile = {}
  if (username) body.username = username
  if (phone) body.phone = phone
  if (signature) body.signature = signature
  if (password) body.password = password
  const { ok } = await safeFetch<unknown>(`${API_BASE}/users/me/profile`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  return ok
}

export type TokenValidationResult =
  | { state: 'valid'; tokens: Partial<Tokens> | null }
  | { state: 'invalid' }
  | { state: 'unavailable' }

/**
 * 单次 token 校验。
 * <p>必须区分两类失败：
 * - `invalid`：401 / 业务码非 0——凭证确实失效，可以登出；
 * - `unavailable`：网络错误、超时、5xx——网关或后端暂时不可用，**不能据此登出**，
 *   否则一次 Cloudflare 503/重启窗口就会把用户踢回首页。
 */
async function requestTokenValidation(): Promise<TokenValidationResult> {
  const { ok, status, body } = await safeFetch<Partial<Tokens>>(`${API_BASE}/auth/validate`, { method: 'GET' })
  if (!ok) {
    // safeFetch 在网络错误/请求中断时返回 status=0
    return status === 0 || status >= 500 ? { state: 'unavailable' } : { state: 'invalid' }
  }
  if (body?.code !== 0) return { state: 'invalid' }
  const data = body.data && typeof body.data === 'object' ? body.data : null
  if (data) {
    // 新接口返回 token 对：刷新后回写 store
    const accessToken = String(data.accessToken || '')
    const refreshToken = String(data.refreshToken || '')
    if (accessToken) {
      store.setToken(accessToken)
      if (refreshToken) store.setRefreshToken(refreshToken)
    }
  }
  // 只要 code=0 就表示验证通过，即使没有返回新 token
  return { state: 'valid', tokens: data }
}

const VALIDATION_RETRY_DELAY_MS = 300

/** 校验 token；瞬时故障（网络/5xx）重试两次，避免网关抖动把用户踢下线 */
export async function validateToken(): Promise<TokenValidationResult> {
  for (let attempt = 0; attempt < 3; attempt++) {
    const result = await requestTokenValidation()
    if (result.state !== 'unavailable') return result
    if (attempt < 2) {
      await new Promise(resolve => setTimeout(resolve, VALIDATION_RETRY_DELAY_MS * (attempt + 1)))
    }
  }
  return { state: 'unavailable' }
}

export async function sendResetCode(email: string) {
  if (!email) return { ok: false, error: '请输入邮箱地址' }
  const { ok, body } = await safeFetch<unknown>(`${API_BASE}/auth/password/reset/code`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
  })
  const error = businessError(ok, body, '验证码发送失败')
  if (error) return { ok: false, error }
  return { ok: true }
}

export async function resetPassword(email: string, code: string, newPassword: string) {
  if (!email || !code || !newPassword) return { ok: false, error: '请填写完整信息' }
  const { ok, body } = await safeFetch<boolean>(`${API_BASE}/auth/password/reset`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, code, newPassword })
  })
  const error = businessError(ok, body, '重置失败')
  if (error) return { ok: false, error }
  return { ok: true }
}

export async function getFriends() {
  const { ok, body } = await safeFetch<User[]>(`${API_BASE}/friends`, { method: 'GET' })
  if (!ok) return []
  return body?.data || []
}

export async function getGroupDetail(id: Id) {
  const { ok, body } = await safeFetch<Group>(`${API_BASE}/groups/${id}`, { method: 'GET' })
  if (!ok) return null
  return body
}

// 示例：调用 agent stream（后续可以改为 SSE 或 WebTransport 分块）
export async function callAgentStream(text: string, onTokenChunk: (chunk: string) => void = () => {}) {
  try {
    const headers: Record<string, string> = { 'Content-Type': 'text/plain' }
    if (store.state.token) {
      headers.Authorization = `Bearer ${store.state.token}`
    }
    const res = await fetch(`${API_BASE}/agent/nl/stream`, {
      method: 'POST', headers, body: text
    })
    if (!res.ok || !res.body) return ''
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let result = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      onTokenChunk(chunk)
      result += chunk
    }
    return result
  } catch (e) {
    return ''
  }
}

// Token 配额 & 购买 → 已提取到 api/quota.ts
export { getTokenQuota, getTokenPrices, purchaseTokens, getPurchaseStatus }
// 客服 AI 副驾 → 已提取到 api/copilot.ts
export * from './copilot'

export async function getOnlineStatus(userIds: Id[]) {
  const { ok, body } = await safeFetch<Record<string, boolean>>(`${API_BASE}/users/online-status`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userIds: userIds.join(',') })
  })
  if (!ok) return {}
  return body?.data || {}
}

export default {
  ...copilotApi,
  login,
  register,
  getCurrentUser,
  validateToken,
  getOfflineMessages,
  confirmOfflineMessages,
  getRecentMessages,
  addFriend,
  searchUsers,
  getFriendRequests,
  acceptFriend,
  rejectFriend,
  addGroup,
  getFriends,
  getUserDetail,
  getGroupDetail,
  getMyGroups,
  callAgentStream,
  sendVerifyCode,
  verifyRegister,
  sendResetCode,
  resetPassword,
  refreshToken,
  syncMessages,
  getTokenQuota,
  getTokenPrices,
  purchaseTokens,
  getPurchaseStatus,
  getFriendNotifications,
  markFriendNotificationsRead,
  getOnlineStatus,
  updateUserProfile
}

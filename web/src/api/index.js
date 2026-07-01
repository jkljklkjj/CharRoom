import store from '../store'

const API_BASE = import.meta.env.VITE_API_BASE || 'https://chatlite.xin/api'

// 刷新token锁，防止多个请求同时刷新
let isRefreshing = false
// 等待刷新的请求队列
let refreshQueue = []

/**
 * 处理刷新成功后的请求重试
 */
function onRefreshSuccess(newToken) {
  refreshQueue.forEach(callback => callback(newToken))
  refreshQueue = []
  isRefreshing = false
}

/**
 * 处理刷新失败后的逻辑
 */
function onRefreshFailure() {
  refreshQueue = []
  isRefreshing = false
  // 清除登录状态，跳转到登录页
  store.clearAll()
  store.setToken('')
  store.setRefreshToken('')
  store.setAccountId('')
  localStorage.removeItem('charroom_token')
  localStorage.removeItem('charroom_refreshToken')
  localStorage.removeItem('charroom_accountId')
  window.location.href = '/login'
}

async function safeFetch(url, options = {}) {
  try {
    // 自动添加Authorization头
    const headers = { ...options.headers }
    if (store.state.token && !headers.Authorization) {
      headers.Authorization = `Bearer ${store.state.token}`
    }

    const res = await fetch(url, { ...options, headers })

    // 如果返回401，尝试刷新token
    if (res.status === 401 && store.state.refreshToken) {
      // 如果正在刷新，把当前请求加入队列等待
      if (isRefreshing) {
        return new Promise(resolve => {
          refreshQueue.push((newToken) => {
            // 使用新token重试请求
            headers.Authorization = `Bearer ${newToken}`
            resolve(safeFetch(url, { ...options, headers }))
          })
        })
      }

      isRefreshing = true
      try {
        // 调用刷新接口
        const refreshResult = await refreshToken(store.state.refreshToken)
        if (refreshResult && refreshResult.accessToken) {
          // 刷新成功，更新store和本地存储
          const { accessToken, refreshToken: newRefreshToken } = refreshResult
          store.setToken(accessToken)
          store.setRefreshToken(newRefreshToken)
          localStorage.setItem('charroom_token', accessToken)
          localStorage.setItem('charroom_refreshToken', newRefreshToken)

          // 重试当前请求
          headers.Authorization = `Bearer ${accessToken}`
          const retryRes = await fetch(url, { ...options, headers })

          // 通知队列中的其他请求重试
          onRefreshSuccess(accessToken)

          if (!retryRes.ok) return { ok: false, status: retryRes.status, body: null }
          const text = await retryRes.text()
          try {
            return { ok: true, status: retryRes.status, body: JSON.parse(text) }
          } catch (_){
            return { ok: true, status: retryRes.status, body: text }
          }
        } else {
          // 刷新失败，退出登录
          onRefreshFailure()
          return { ok: false, status: 401, body: null }
        }
      } catch (e) {
        // 刷新过程出错，退出登录
        onRefreshFailure()
        return { ok: false, status: 401, body: null }
      }
    }

    if (!res.ok) return { ok: false, status: res.status, body: null }
    const text = await res.text()
    try {
      return { ok: true, status: res.status, body: JSON.parse(text) }
    } catch (_){
      return { ok: true, status: res.status, body: text }
    }
  } catch (e) {
    return { ok: false, status: 0, body: null }
  }
}

function getDeviceId() {
  const key = 'charroom_device_id'
  let id = localStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
    localStorage.setItem(key, id)
  }
  return id
}

function getDeviceType() { return 'web' }

export async function login(account, password) {
  if (!account || !password) return null
  const { ok, body } = await safeFetch(`${API_BASE}/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ account, password, deviceType: getDeviceType(), deviceId: getDeviceId() })
  })
  if (!ok) return null

  const data = body?.data
  // 兼容新后端：data = { accessToken, refreshToken }
  if (data && typeof data === 'object') {
    const accessToken = String(data.accessToken || '')
    const refreshToken = String(data.refreshToken || '')
    if (accessToken) {
      return { accessToken, refreshToken }
    }
  }
  // 兼容旧后端：data 直接是 access token
  if (typeof data === 'string' && data) {
    return { accessToken: data, refreshToken: '' }
  }
  return null
}

export async function refreshToken(refreshToken) {
  if (!refreshToken) return null
  const { ok, body } = await safeFetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  })
  if (!ok) return null
  const data = body?.data
  if (!data || typeof data !== 'object') return null
  const accessToken = String(data.accessToken || '')
  const nextRefreshToken = String(data.refreshToken || '')
  if (!accessToken) return null
  return { accessToken, refreshToken: nextRefreshToken }
}

export async function register(username, password) {
  if (!username || !password) return -1
  // 注册使用后端的 /user/register，传入 User 对象。只提供 username 和 password，email/phone 可留空。
  const userObj = { username, password, email: '', phone: '' }
  const { ok, body } = await safeFetch(`${API_BASE}/auth/register/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(userObj)
  })
  if (!ok) return -1
  return body?.data ?? -1
}

export async function sendVerifyCode(email) {
  if (!email) return false
  const { ok, body } = await safeFetch(`${API_BASE}/auth/verify-code`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
  })
  if (!ok) return false
  return body?.data === true
}

export async function verifyRegister(email, code, password) {
  if (!email || !code || !password) return -1
  const { ok, body } = await safeFetch(`${API_BASE}/auth/register/verify`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, code, password })
  })
  if (!ok) return -1
  return body?.data ?? -1
}

export async function getOfflineMessages(pageSize) {
  const params = pageSize ? `?pageSize=${pageSize}` : ''
  const { ok, body } = await safeFetch(`${API_BASE}/messages/offline${params}`, { method: 'GET' })
  if (!ok) return []
  return body?.data || []
}

/**
 * 增量同步消息（基于 seqId 游标）。
 */
export async function syncMessages(conversationId, lastSeqId, limit = 50) {
  const deviceType = localStorage.getItem('charroom_deviceType') || 'web'
  const { ok, body } = await safeFetch(`${API_BASE}/sync/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, lastSeqId, limit, deviceType })
  })
  if (!ok || !body?.data) return { messages: [], nextSeqId: lastSeqId, hasMore: false }
  return body.data
}

export async function getMyGroups() {
  const { ok, body } = await safeFetch(`${API_BASE}/groups`, { method: 'GET' })
  if (!ok) return []
  return body?.data || body || []
}

export async function addFriend(account) {
  const { ok } = await safeFetch(`${API_BASE}/friends`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ account })
  })
  return ok
}

export async function getFriendRequests() {
  const { ok, body } = await safeFetch(`${API_BASE}/friends/requests`, { method: 'GET' })
  if (!ok) return []
  // 支持 ApiResponse 包装或直接返回数组
  return body?.data || body || []
}

export async function acceptFriend(requesterId) {
  console.log('acceptFriend 传入参数:', requesterId, '类型:', typeof requesterId)
  const friendId = parseInt(requesterId)
  console.log('acceptFriend 转换后参数:', friendId, '类型:', typeof friendId)
  const body = JSON.stringify({ friendId })
  console.log('acceptFriend 请求体:', body)
  const { ok } = await safeFetch(`${API_BASE}/friends/accept`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body
  })
  console.log('acceptFriend 返回结果:', ok)
  return ok
}

export async function rejectFriend(requesterId) {
  console.log('rejectFriend 传入参数:', requesterId, '类型:', typeof requesterId)
  const friendId = parseInt(requesterId)
  console.log('rejectFriend 转换后参数:', friendId, '类型:', typeof friendId)
  const body = JSON.stringify({ friendId })
  console.log('rejectFriend 请求体:', body)
  const { ok } = await safeFetch(`${API_BASE}/friends/reject`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body
  })
  console.log('rejectFriend 返回结果:', ok)
  return ok
}

export async function addGroup(groupId) {
  const { ok } = await safeFetch(`${API_BASE}/groups/${groupId}/join`, { method: 'POST' })
  return ok
}

export async function getUserDetail(id) {
  const { ok, body } = await safeFetch(`${API_BASE}/friends/${id}`, { method: 'GET' })
  if (!ok) return null
  return body?.data || body || null
}

export async function delFriend(friendId) {
  const { ok } = await safeFetch(`${API_BASE}/friends/${friendId}`, { method: 'DELETE' })
  return ok
}

export async function getCurrentUser() {
  const { ok, body } = await safeFetch(`${API_BASE}/users/me`, { method: 'GET' })
  if (!ok) return null
  return body?.data || body || null
}

export async function validateToken() {
  try {
    const { ok, body } = await safeFetch(`${API_BASE}/auth/validate`, { method: 'GET' })
    if (!ok) return null
    if (body?.code === 0) {
      // 验证成功
      if (body?.data && typeof body.data === 'object') {
        // 新接口：返回token对，更新store中的token
        const accessToken = String(body.data.accessToken || '')
        const refreshToken = String(body.data.refreshToken || '')
        if (accessToken) {
          store.setToken(accessToken)
          if (refreshToken) {
            store.setRefreshToken(refreshToken)
          }
          return body.data // 返回token对象
        }
      }
      // 只要code=0就表示验证通过，即使没有返回新token
      return { valid: true }
    }
    // 验证失败（包括401、token过期、旧接口验证失败等）统一返回null
    return null
  } catch (e) {
    // 任何异常都返回null
    return null
  }
}

export async function getFriends() {
  const { ok, body } = await safeFetch(`${API_BASE}/friends`, { method: 'GET' })
  if (!ok) return []
  return body?.data || body || []
}

export async function getGroupDetail(id) {
  const { ok, body } = await safeFetch(`${API_BASE}/groups/${id}`, { method: 'GET' })
  if (!ok) return null
  return body
}

// 示例：调用 agent stream（后续可以改为 SSE 或 websocket 分块）
export async function callAgentStream(text, onTokenChunk = (chunk) => {}) {
  try {
    const res = await fetch(`${API_BASE}/agent/nl/stream`, {
      method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: text
    })
    if (!res.ok) return ''
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

/**
 * Token 配额 & 购买
 */
export async function getTokenQuota() {
  const res = await safeFetch(`${API_BASE}/agent/quota`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}
export async function getTokenPrices() {
  const res = await safeFetch(`${API_BASE}/agent/quota/prices`, { method: 'GET' })
  return res.ok ? (res.body?.data || null) : null
}
export async function purchaseTokens(amountFen) {
  const res = await safeFetch(`${API_BASE}/agent/quota/purchase`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount: amountFen })
  })
  return res.ok ? (res.body?.data || res.body) : null
}

export default {
  login,
  register,
  getCurrentUser,
  validateToken,
  getOfflineMessages,
  addFriend,
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
  refreshToken,
  syncMessages,
  getTokenQuota,
  getTokenPrices,
  purchaseTokens
}

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

async function safeFetch(url, options) {
  try {
    const res = await fetch(url, options)
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

export async function login(account, password) {
  if (!account || !password) return ''
  const { ok, body } = await safeFetch(`${API_BASE}/user/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ account, password })
  })
  if (!ok) return ''
  return body?.data || ''
}

export async function register(username, password) {
  if (!username || !password) return -1
  // 注册使用后端的 /user/register，传入 User 对象。只提供 username 和 password，email/phone 可留空。
  const userObj = { username, password, email: '', phone: '' }
  const { ok, body } = await safeFetch(`${API_BASE}/user/register`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(userObj)
  })
  if (!ok) return -1
  return body?.data ?? -1
}

export async function sendVerifyCode(email) {
  if (!email) return false
  const { ok, body } = await safeFetch(`${API_BASE}/user/sendVerifyCode`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email })
  })
  if (!ok) return false
  return body?.data === true
}

export async function verifyRegister(email, code, password) {
  if (!email || !code || !password) return -1
  const { ok, body } = await safeFetch(`${API_BASE}/user/verifyRegister`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, code, password })
  })
  if (!ok) return -1
  return body?.data ?? -1
}

export async function getOfflineMessages() {
  const { ok, body } = await safeFetch(`${API_BASE}/messages/offline`, { method: 'GET' })
  if (!ok) return []
  return body || []
}

export async function addFriend(account) {
  const { ok } = await safeFetch(`${API_BASE}/friend/add`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ friendId: account })
  })
  return ok
}

export async function getFriendRequests() {
  const { ok, body } = await safeFetch(`${API_BASE}/friend/requests`, { method: 'GET' })
  if (!ok) return []
  // 支持 ApiResponse 包装或直接返回数组
  return body?.data || body || []
}

export async function acceptFriend(requesterId) {
  const { ok } = await safeFetch(`${API_BASE}/friend/accept`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ friendId: requesterId })
  })
  return ok
}

export async function addGroup(groupId) {
  const { ok } = await safeFetch(`${API_BASE}/group/add`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ groupId })
  })
  return ok
}

export async function getUserDetail(id) {
  const { ok, body } = await safeFetch(`${API_BASE}/friend/get?id=${id}`, { method: 'POST' })
  if (!ok) return null
  return body
}

export async function getGroupDetail(id) {
  const { ok, body } = await safeFetch(`${API_BASE}/group/get/detail?id=${id}`, { method: 'GET' })
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

export default {
  login,
  register,
  getOfflineMessages,
  addFriend,
  addGroup,
  getUserDetail,
  getGroupDetail,
  callAgentStream,
  sendVerifyCode,
  verifyRegister
}

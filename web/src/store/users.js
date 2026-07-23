import { state } from './state'
import { getConversationKey, isGroupConversationId, normalizeTimeValue,
         loadGroupConversation, loadPrivateConversation, sanitizeId } from './storage'
import i18n from '../i18n'

// ── 会话状态 ──────────────────────────────────

// 缓存会话预览，避免每次用户列表变化都读 localStorage
const previewCache = new Map() // key: "accountId:conversationId" → { lastIncomingMessageTime, loadedAt }

function loadConversationPreview(accountId, conversationId, forceRefresh = false) {
  const id = sanitizeId(conversationId)
  if (!accountId || !id) {
    return { lastIncomingMessageTime: 0, unreadCount: 0 }
  }

  const cacheKey = `${accountId}:${id}`
  if (!forceRefresh && previewCache.has(cacheKey)) {
    return previewCache.get(cacheKey)
  }

  const isGroup = isGroupConversationId(id)
  const items = isGroup
    ? loadGroupConversation(accountId, Math.abs(Number(id)))
    : loadPrivateConversation(accountId, id)

  let lastIncomingMessageTime = 0
  items.forEach(item => {
    const messageTime = normalizeTimeValue(item.time || item.timestamp)
    const isIncoming = isGroup
      ? String(item.user) !== String(accountId)
      : String(item.user) !== 'you'
    if (isIncoming) {
      lastIncomingMessageTime = Math.max(lastIncomingMessageTime, messageTime)
    }
  })

  const preview = { lastIncomingMessageTime, unreadCount: 0 }
  previewCache.set(cacheKey, preview)
  return preview
}

export function rebuildConversationStates(forceRefresh = false) {
  if (!state.accountId || !Array.isArray(state.users)) {
    state.conversationStates = {}
    return
  }

  const existingStates = state.conversationStates || {}
  const nextStates = {}
  state.users.forEach(user => {
    if (!user || user.id == null) return
    const preview = loadConversationPreview(state.accountId, user.id, forceRefresh)
    const existing = existingStates[String(user.id)] || {}
    nextStates[String(user.id)] = {
      lastIncomingMessageTime: preview.lastIncomingMessageTime,
      unreadCount: existing.unreadCount || 0
    }
  })
  state.conversationStates = nextStates
}

/**
 * 当消息变化时调用，清除特定会话的预览缓存。
 * 这样下次 rebuildConversationStates 时会重新读取 localStorage。
 */
export function invalidateConversationPreview(conversationId) {
  if (!state.accountId) return
  const id = sanitizeId(conversationId)
  if (id) {
    previewCache.delete(`${state.accountId}:${id}`)
  }
}

export function updateConversationState(conversationId, patch = {}) {
  const key = getConversationKey(conversationId)
  if (!key) return
  const current = state.conversationStates[key] || { lastIncomingMessageTime: 0, unreadCount: 0 }
  state.conversationStates[key] = {
    lastIncomingMessageTime: Math.max(current.lastIncomingMessageTime || 0, patch.lastIncomingMessageTime || 0),
    unreadCount: patch.unreadCount != null
      ? Math.max(0, patch.unreadCount)
      : Math.max(0, (current.unreadCount || 0) + (patch.unreadDelta || 0))
  }
}

export function clearConversationUnread(conversationId) {
  const key = getConversationKey(conversationId)
  if (!key || !state.conversationStates[key]) return
  state.conversationStates = {
    ...state.conversationStates,
    [key]: {
      ...state.conversationStates[key],
      unreadCount: 0
    }
  }
}

// ── 用户管理 ──────────────────────────────────

export function setUsers(list) {
  state.users = list
  // 用户列表完全替换，需要强制刷新预览缓存
  rebuildConversationStates(true)
}

export function setGroups(list) {
  state.groups = list
}

export function mergeUsers(incoming) {
  if (!Array.isArray(incoming)) return

  const existingById = new Map(state.users.map(u => [u.id, u]))
  let changed = false

  for (const user of incoming) {
    const existing = existingById.get(user.id)
    if (!existing) {
      existingById.set(user.id, { ...user })
      changed = true
    } else {
      let modified = false
      if (existing.online !== user.online) { existing.online = user.online; modified = true }
      if (existing.username !== user.username) { existing.username = user.username; modified = true }
      if (existing.status !== user.status) { existing.status = user.status; modified = true }
      if (existing.avatarUrl !== user.avatarUrl) { existing.avatarUrl = user.avatarUrl; modified = true }
      if (existing.signature !== user.signature) { existing.signature = user.signature; modified = true }
      if (modified) changed = true
    }
  }

  // 注意：merge 只做合并，不删除已有用户（缓存可能不完整）
  // 显式删除应通过 removeUser() 或 fetchFriends() 后的 setUsers() 处理

  if (changed) {
    state.users = Array.from(existingById.values())
    setTimeout(() => rebuildConversationStates(), 0)
  }
}

export function cacheUsers(users) {
  try {
    if (state.accountId) {
      const key = `charroom_users_${state.accountId}`
      localStorage.setItem(key, JSON.stringify({
        version: Date.now(),
        users: users.map(u => ({
          id: u.id,
          username: u.username,
          online: u.online,
          status: u.status,
          avatarUrl: u.avatarUrl,
          avatarKey: u.avatarKey,  // 存储 avatarKey 用于缓存失效
          signature: u.signature
        }))
      }))
    }
  } catch (_) { /* localStorage 满时静默失败 */ }
}

export function loadCachedUsers() {
  try {
    if (state.accountId) {
      const raw = localStorage.getItem(`charroom_users_${state.accountId}`)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (parsed && Array.isArray(parsed.users)) return parsed.users
      }
    }
  } catch (_) { /* ignore */ }
  return []
}

export function addUser(u) {
  if (!state.users.some(x => x.id === u.id)) {
    state.users.push(u)
    // 新用户需要强制刷新预览缓存
    rebuildConversationStates(true)
  }
}

export function removeUser(id) {
  const idx = state.users.findIndex(u => u.id === id)
  if (idx === -1) return
  state.users.splice(idx, 1)
  delete state.conversationStates[id]
  state.messages = state.messages.filter(m => m.user !== id.toString())
  if (state.selectedChatId === id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    clearConversationUnread(id)
  }
}

export function updateUserOnlineStatus(userId, online) {
  const idx = state.users.findIndex(user => user.id === userId)
  if (idx >= 0) {
    state.users[idx] = {
      ...state.users[idx],
      online: online,
      status: online ? i18n.global.t('sidebar.online') : i18n.global.t('sidebar.offline')
    }
  }
}

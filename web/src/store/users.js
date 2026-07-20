import { state } from './state'
import { getConversationKey, isGroupConversationId, normalizeTimeValue,
         loadGroupConversation, loadPrivateConversation, sanitizeId } from './storage'
import i18n from '../i18n'

// ── 会话状态 ──────────────────────────────────

function loadConversationPreview(accountId, conversationId) {
  const id = sanitizeId(conversationId)
  if (!accountId || !id) {
    return { lastIncomingMessageTime: 0, unreadCount: 0 }
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

  return { lastIncomingMessageTime, unreadCount: 0 }
}

export function rebuildConversationStates() {
  if (!state.accountId || !Array.isArray(state.users)) {
    state.conversationStates = {}
    return
  }

  const existingStates = state.conversationStates || {}
  const nextStates = {}
  state.users.forEach(user => {
    if (!user || user.id == null) return
    const preview = loadConversationPreview(state.accountId, user.id)
    const existing = existingStates[String(user.id)] || {}
    nextStates[String(user.id)] = {
      lastIncomingMessageTime: preview.lastIncomingMessageTime,
      unreadCount: existing.unreadCount || 0
    }
  })
  state.conversationStates = nextStates
}

export function updateConversationState(conversationId, patch = {}) {
  const key = getConversationKey(conversationId)
  if (!key) return
  const current = state.conversationStates[key] || { lastIncomingMessageTime: 0, unreadCount: 0 }
  state.conversationStates = {
    ...state.conversationStates,
    [key]: {
      lastIncomingMessageTime: Math.max(current.lastIncomingMessageTime || 0, patch.lastIncomingMessageTime || 0),
      unreadCount: patch.unreadCount != null
        ? Math.max(0, patch.unreadCount)
        : Math.max(0, (current.unreadCount || 0) + (patch.unreadDelta || 0))
    }
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
  rebuildConversationStates()
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

  const incomingIds = new Set(incoming.map(u => u.id))
  for (const [id, _u] of existingById) {
    if (!incomingIds.has(id) && id > 0) {
      existingById.delete(id)
      changed = true
    }
  }

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
    rebuildConversationStates()
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
  state.users = state.users.map(user => {
    if (user.id === userId) {
      return {
        ...user,
        online: online,
        status: online ? i18n.global.t('sidebar.online') : i18n.global.t('sidebar.offline')
      }
    }
    return user
  })
}

// ── 头像缓存 ──────────────────────────────────

const avatarCache = new Map()

export function getCachedAvatar(url) {
  if (!url) return null
  if (avatarCache.has(url)) return avatarCache.get(url)
  try {
    const key = `charroom_avatar_${btoa(url).slice(0, 40)}`
    const cached = localStorage.getItem(key)
    if (cached) {
      avatarCache.set(url, cached)
      return cached
    }
  } catch (_) {}
  return null
}

export function setCachedAvatar(url, dataUrl) {
  if (!url || !dataUrl) return
  avatarCache.set(url, dataUrl)
  try {
    const key = `charroom_avatar_${btoa(url).slice(0, 40)}`
    localStorage.setItem(key, dataUrl)
  } catch (_) {}
}

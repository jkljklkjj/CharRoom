import type { Id, User, Group, ConversationState, ConversationPatch } from '../types/chat'
import { state } from './state'
import { getConversationKey, sanitizeId } from './storage'
import { getAllConversationMeta } from '../utils/messageDB'
import i18n from '../i18n'

// ── 会话状态 ──────────────────────────────────

// 会话预览缓存：accountId → Map<conversationId, lastIncomingMessageTime>
// 数据源为 IndexedDB conversation_meta（localStorage 旧 key 在迁移后已无写入）
const previewCache = new Map<string, Map<string, number>>()

/** 会话 key 统一约定：私聊 = 对方 id 字符串；群聊 = "group:<absId>" */
function normalizeConversationId(conversationId: Id): string {
  const id = sanitizeId(conversationId)
  if (!id) return ''
  return Number(id) < 0 ? `group:${Math.abs(Number(id))}` : id
}

async function loadConversationMetaMap(accountId: Id, forceRefresh = false): Promise<Map<string, number>> {
  const cacheKey = String(accountId)
  if (!forceRefresh && previewCache.has(cacheKey)) {
    return previewCache.get(cacheKey)!
  }
  let map = new Map<string, number>()
  try {
    const metas = await getAllConversationMeta(accountId)
    map = new Map(metas.map(m => [m.conversationId, m.lastIncomingMessageTime || 0]))
  } catch (_) {
    // IndexedDB 不可用时回退空 map（预览时间为 0，聊天本身不受影响）
  }
  previewCache.set(cacheKey, map)
  return map
}

/**
 * 重建会话预览（最后消息时间 + 未读数）。
 * 数据源：IndexedDB 会话元数据；未读数始终保留内存中的实时值。
 * 异步执行，调用方无需 await（结果会写入 state.conversationStates）。
 */
export async function rebuildConversationStates(forceRefresh = false) {
  if (!state.accountId || !Array.isArray(state.users)) {
    state.conversationStates = {}
    return
  }
  let metaMap = new Map<string, number>()
  try {
    metaMap = await loadConversationMetaMap(state.accountId, forceRefresh)
  } catch (_) { /* 保留空 map */ }

  // 从当前 state 起步，保留重建期间的新 patch（unreadCount / 更新的时间）
  const nextStates: Record<string, ConversationState> = { ...state.conversationStates }
  state.users.forEach(user => {
    if (!user || user.id == null) return
    const key = String(user.id)
    const convId = normalizeConversationId(key)
    const dbTime = convId ? (metaMap.get(convId) || 0) : 0
    const current = nextStates[key] || ({} as ConversationState)
    nextStates[key] = {
      lastIncomingMessageTime: Math.max(dbTime, current.lastIncomingMessageTime || 0),
      unreadCount: current.unreadCount || 0
    }
  })
  state.conversationStates = nextStates
}

/**
 * 消息变化时清除对应会话的预览缓存，
 * 下次 rebuildConversationStates 时重新读取 IndexedDB。
 */
export function invalidateConversationPreview(conversationId: Id) {
  if (!state.accountId) return
  const map = previewCache.get(String(state.accountId))
  if (!map) return
  const convId = normalizeConversationId(conversationId)
  if (convId) map.delete(convId)
}

export function updateConversationState(conversationId: Id, patch: ConversationPatch = {}) {
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

export function clearConversationUnread(conversationId: Id) {
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

export function setUsers(list: readonly User[]) {
  state.users = [...list]
  // 用户列表完全替换，需要强制刷新预览缓存
  rebuildConversationStates(true)
}

export function setGroups(list: readonly Group[]) {
  state.groups = [...list]
}

export function mergeUsers(incoming: readonly User[]) {
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

export function cacheUsers(users: readonly User[]) {
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

export function loadCachedUsers(): User[] {
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

export function addUser(u: User) {
  if (!state.users.some(x => x.id === u.id)) {
    state.users.push(u)
    // 新用户需要强制刷新预览缓存
    rebuildConversationStates(true)
  }
}

export function removeUser(id: Id) {
  if (!state.users.some(u => u.id === id)) return
  state.users = state.users.filter(u => u.id !== id)
  delete state.conversationStates[id]
  state.messages = state.messages.filter(m => m.user !== id.toString())
  if (state.selectedChatId === id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    clearConversationUnread(id)
  }
}

export function updateUserOnlineStatus(userId: Id, online: boolean) {
  const idx = state.users.findIndex(user => user.id === userId)
  if (idx >= 0) {
    state.users[idx] = {
      ...state.users[idx],
      online: online,
      status: online ? i18n.global.t('sidebar.online') : i18n.global.t('sidebar.offline')
    }
  }
}

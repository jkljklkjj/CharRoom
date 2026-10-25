import type { Id, User, Group, ConversationState, ConversationPatch } from '../types/chat'
import { state } from './state'
import { getConversationKey, sanitizeId } from './storage'
import { getAllConversationMeta, getLastMessage, setConversationPreview } from '../utils/messageDB'
import { conversationPreviewText } from '../utils/format'
import i18n from '../i18n'

// ── 会话状态 ──────────────────────────────────

// 会话预览缓存：accountId → Map<conversationId, { 最后消息时间, 预览文本 }>
// 数据源为 IndexedDB conversation_meta（localStorage 旧 key 在迁移后已无写入）
interface ConversationPreviewEntry { time: number; preview: string }
const previewCache = new Map<string, Map<string, ConversationPreviewEntry>>()

/** 会话 key 统一约定：私聊 = 对方 id 字符串；群聊 = "group:<absId>" */
function normalizeConversationId(conversationId: Id): string {
  const id = sanitizeId(conversationId)
  if (!id) return ''
  return Number(id) < 0 ? `group:${Math.abs(Number(id))}` : id
}

async function loadConversationMetaMap(accountId: Id, forceRefresh = false): Promise<Map<string, ConversationPreviewEntry>> {
  const cacheKey = String(accountId)
  if (!forceRefresh && previewCache.has(cacheKey)) {
    return previewCache.get(cacheKey)!
  }
  let map = new Map<string, ConversationPreviewEntry>()
  try {
    const metas = await getAllConversationMeta(accountId)
    map = new Map(metas.map(m => [m.conversationId, {
      time: m.lastIncomingMessageTime || 0,
      preview: m.lastMessagePreview || ''
    }]))
  } catch (_) {
    // IndexedDB 不可用时回退空 map（预览时间为 0，聊天本身不受影响）
  }
  previewCache.set(cacheKey, map)
  return map
}

/**
 * 升级前写入的会话元数据没有预览文本：读该会话最新一条消息补上，只补一次。
 * 异步、失败静默；补写的文本同时回写 IndexedDB 与内存预览缓存。
 */
function backfillMissingPreviews(accountId: Id, metaMap: Map<string, ConversationPreviewEntry>) {
  const missing = [...metaMap.entries()]
    .filter(([, entry]) => entry.time > 0 && !entry.preview)
    .map(([convId]) => convId)
  if (!missing.length) return
  Promise.all(missing.map(async convId => {
    const preview = conversationPreviewText(await getLastMessage(accountId, convId))
    if (preview) await setConversationPreview(accountId, convId, preview)
    return { convId, preview }
  })).then(results => {
    for (const { convId, preview } of results) {
      const entry = metaMap.get(convId)
      if (entry && !entry.preview) entry.preview = preview
      // 私聊会话 key 即用户 id，直接回填到已写入的会话状态
      const current = state.conversationStates[convId]
      if (preview && current && !current.lastMessagePreview) {
        state.conversationStates = {
          ...state.conversationStates,
          [convId]: { ...current, lastMessagePreview: preview }
        }
      }
    }
  }).catch(() => { /* 补写失败保持原样，下次 rebuild 再试 */ })
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
  let metaMap = new Map<string, ConversationPreviewEntry>()
  try {
    metaMap = await loadConversationMetaMap(state.accountId, forceRefresh)
  } catch (_) { /* 保留空 map */ }

  // 从当前 state 起步，保留重建期间的新 patch（unreadCount / 更新的时间）
  const nextStates: Record<string, ConversationState> = { ...state.conversationStates }
  state.users.forEach(user => {
    if (!user || user.id == null) return
    const key = String(user.id)
    const convId = normalizeConversationId(key)
    const entry = convId ? metaMap.get(convId) : undefined
    const dbTime = entry?.time || 0
    const current = nextStates[key] || ({} as ConversationState)
    nextStates[key] = {
      lastIncomingMessageTime: Math.max(dbTime, current.lastIncomingMessageTime || 0),
      unreadCount: current.unreadCount || 0,
      // 重建期间新到达的消息（patch 已更新预览）优先于库里的旧预览
      lastMessagePreview: (entry?.preview || '') || current.lastMessagePreview || undefined
    }
  })
  state.conversationStates = nextStates
  backfillMissingPreviews(state.accountId, metaMap)
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
      : Math.max(0, (current.unreadCount || 0) + (patch.unreadDelta || 0)),
    // 只更新未读数等 patch 不应抹掉已有的预览文本
    lastMessagePreview: patch.lastMessagePreview !== undefined ? patch.lastMessagePreview : current.lastMessagePreview
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

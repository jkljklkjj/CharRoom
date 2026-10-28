import type { Id, User, Group, ConversationState, ConversationPatch } from '../types/chat'
import {ConversationId} from '../utils/conversationId'
import {sidebarUsers} from '../utils/contactDirectory'
import { state } from './state'
import { getConversationKey, sanitizeId } from './storage'
import { getAllConversationMeta } from '../utils/messageDB'
import { conversationPreviewText } from '../utils/format'
import i18n from '../i18n'
import {sessionEpoch} from '../services/sessionEpoch'

/** Apply an authoritative membership snapshot while keeping live presence and stable row identities. */
export function applyDirectorySnapshot(snapshot: import('../services/contactDirectorySync').DirectorySnapshot) {
  const previous=new Map(state.users.map(user=>[String(user.id),user]))
  const next=snapshot.users.map(user=>{
    const existing=previous.get(String(user.id))
    if(!existing)return {...user}
    const changed=Object.entries(user).some(([key,value])=>value!==(existing as unknown as Record<string,unknown>)[key])
    if(changed)Object.assign(existing,user)
    return existing
  })
  if(next.length!==state.users.length || next.some((user,index)=>user!==state.users[index]))state.users=next
  const previousGroups=new Map(state.groups.map(group=>[String(group.id),group]))
  const groups=snapshot.groups.map(group=>{
    const existing=previousGroups.get(String(group.id))
    if(!existing)return {...group}
    Object.assign(existing,group);return existing
  })
  if(groups.length!==state.groups.length || groups.some((group,index)=>group!==state.groups[index]))state.groups=groups
  void rebuildConversationStates()
}

// ── 会话状态 ──────────────────────────────────

// 会话预览缓存：accountId → Map<conversationId, { 最后消息时间, 预览文本 }>
// 数据源为 IndexedDB conversation_meta（localStorage 旧 key 在迁移后已无写入）
interface ConversationPreviewEntry { time: number; preview: string }
const previewCache = new Map<string, Map<string, ConversationPreviewEntry>>()
export function clearConversationPreviewCache(){previewCache.clear()}

/** Convert a typed UI key at the existing IndexedDB schema boundary. */
function normalizeConversationId(conversationId: Id): string {
  const id = sanitizeId(conversationId)
  if (!id) return ''
  return ConversationId.parse(id).historyKey
}

async function loadConversationMetaMap(accountId: Id, forceRefresh = false): Promise<Map<string, ConversationPreviewEntry>> {
  const cacheKey = String(accountId),session=sessionEpoch.capture()
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
  if(session.isCurrent() && String(state.accountId)===cacheKey)previewCache.set(cacheKey, map)
  return map
}

/** Seed ordering before the cached directory becomes visible. */
export async function hydrateDirectoryPreviews(accountId:string) {
  const session=sessionEpoch.capture(),meta=await loadConversationMetaMap(accountId,true)
  if(!session.isCurrent()||String(state.accountId)!==accountId)return
  const next={...state.conversationStates}
  for(const [conversation,value] of meta) {
    const key=ConversationId.fromHistoryKey(conversation).key
    const current=next[key]
    next[key]={lastIncomingMessageTime:Math.max(value.time,current?.lastIncomingMessageTime||0),unreadCount:current?.unreadCount||0,
      lastMessagePreview:(current?.lastIncomingMessageTime||0)>=value.time?current?.lastMessagePreview||value.preview:value.preview}
  }
  state.conversationStates=next
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
  const accountId = state.accountId,session=sessionEpoch.capture()
  let metaMap = new Map<string, ConversationPreviewEntry>()
  try {
    metaMap = await loadConversationMetaMap(accountId, forceRefresh)
  } catch (_) { /* 保留空 map */ }
  if (state.accountId !== accountId || !session.isCurrent()) return

  // 从当前 state 起步，保留重建期间的新 patch（unreadCount / 更新的时间）
  const nextStates: Record<string, ConversationState> = { ...state.conversationStates }
  sidebarUsers(state.users,state.groups).forEach(user => {
    if (!user || user.id == null) return
    const key = ConversationId.forContact(user).key
    const convId = normalizeConversationId(key)
    const entry = convId ? metaMap.get(convId) : undefined
    const dbTime = entry?.time || 0
    const current = nextStates[key] || ({} as ConversationState)
    nextStates[key] = {
      lastIncomingMessageTime: Math.max(dbTime, current.lastIncomingMessageTime || 0),
      unreadCount: current.unreadCount || 0,
      // 重建期间新到达的消息（patch 已更新预览）优先于库里的旧预览
      lastMessagePreview: (current.lastIncomingMessageTime||0)>=dbTime ? current.lastMessagePreview||entry?.preview : entry?.preview||current.lastMessagePreview
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
  delete state.conversationStates[ConversationId.privateChat(id).key]
  state.messages = state.messages.filter(m => m.user !== id.toString())
  if (state.selectedChatId === ConversationId.privateChat(id).key) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    clearConversationUnread(ConversationId.privateChat(id).key)
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

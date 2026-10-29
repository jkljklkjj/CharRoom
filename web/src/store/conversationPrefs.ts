import type { Id } from '../types/chat'
import { state } from './state'
import { getConversationKey } from './storage'

/**
 * 会话列表偏好只保存在本机当前账号下：
 * - 置顶：只改变消息页排序；
 * - 删除：只从消息页移除，IndexedDB 里的聊天记录不动，下一条发送/接收消息会自动恢复展示。
 *
 * 键沿用聊天历史的账号前缀，退出登录时由 auth.clearAll 一并清理。
 */
const PREFIX = 'charroom_chat_history_'
const VERSION = 1
const VALID_KEY = /^(user|group):(0|[1-9][0-9]*)$/

interface StoredPrefs { version: number; pinned?: unknown; hidden?: unknown }

function storageKey(accountId: Id) {
  return `${PREFIX}${String(accountId)}_conversation_prefs`
}

function conversationKey(conversationId: Id): string {
  try {
    const key = getConversationKey(conversationId)
    return VALID_KEY.test(key) ? key : ''
  } catch {
    return ''
  }
}

function validKeys(value: unknown): string[] {
  return Array.isArray(value)
    ? [...new Set(value.filter((item): item is string => typeof item === 'string' && VALID_KEY.test(item)))]
    : []
}

export function loadConversationPrefs(accountId: Id = state.accountId) {
  resetConversationPrefs()
  if (!accountId || typeof localStorage === 'undefined') return
  try {
    const raw = localStorage.getItem(storageKey(accountId))
    if (!raw) return
    const saved = JSON.parse(raw) as StoredPrefs
    if (!saved || saved.version !== VERSION) return
    state.pinnedConversations = validKeys(saved.pinned)
    // 置顶优先于隐藏，避免旧数据同时命中时出现一条看不见的置顶会话
    const pinned = new Set(state.pinnedConversations)
    state.hiddenConversations = validKeys(saved.hidden).filter(key => !pinned.has(key))
  } catch {
    // 损坏或被禁用的 localStorage 不应阻止聊天
  }
}

export function resetConversationPrefs() {
  state.pinnedConversations = []
  state.hiddenConversations = []
}

function save() {
  if (!state.accountId || typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(storageKey(state.accountId), JSON.stringify({
      version: VERSION,
      pinned: state.pinnedConversations,
      hidden: state.hiddenConversations
    }))
  } catch {
    // 存储满或被禁用时保留内存状态，不影响本次会话
  }
}

export function isConversationPinned(conversationId: Id) {
  const key = conversationKey(conversationId)
  return Boolean(key) && state.pinnedConversations.includes(key)
}

export function isConversationHidden(conversationId: Id) {
  const key = conversationKey(conversationId)
  return Boolean(key) && state.hiddenConversations.includes(key)
}

export function toggleConversationPin(conversationId: Id) {
  const key = conversationKey(conversationId)
  if (!key) return
  if (state.pinnedConversations.includes(key)) {
    state.pinnedConversations = state.pinnedConversations.filter(item => item !== key)
  } else {
    state.pinnedConversations = [key, ...state.pinnedConversations.filter(item => item !== key)]
    // 置顶一条曾被“删除”的会话时应让它重新可见
    state.hiddenConversations = state.hiddenConversations.filter(item => item !== key)
  }
  save()
}

/** 仅隐藏展示，不触碰消息历史。收到/发送新消息时由 revealConversation 恢复。 */
export function hideConversation(conversationId: Id) {
  const key = conversationKey(conversationId)
  if (!key) return
  state.hiddenConversations = [key, ...state.hiddenConversations.filter(item => item !== key)]
  state.pinnedConversations = state.pinnedConversations.filter(item => item !== key)
  save()
}

export function revealConversation(conversationId: Id) {
  const key = conversationKey(conversationId)
  if (!key || !state.hiddenConversations.includes(key)) return
  state.hiddenConversations = state.hiddenConversations.filter(item => item !== key)
  save()
}

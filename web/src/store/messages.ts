import type { Id, ChatMessage, ConversationPatch } from '../types/chat'
import { state, PAGE_SIZE } from './state'
import { AGENT_USER_ID } from '../constants'
import { getPrivateConversationId, normalizeTimeValue } from './storage'
import { conversationPreviewText } from '../utils/format'
import DOMPurify from 'dompurify'
import { getMessagesPage as dbGetMessagesPage, appendMessage as dbAppendMessage,
         mergeHistoryMessages as dbMergeHistoryMessages, deleteMessage as dbDeleteMessage } from '../utils/messageDB'

// 延迟导入 users.ts 避免循环依赖
let _updateConversationState: ((id: Id, patch: ConversationPatch) => void) | null = null
let _invalidateConversationPreview: ((id: Id) => void) | null = null
let _pendingPatches: { conversationId: Id; patch: ConversationPatch }[] = [] // 缓存首次调用的 patch，等导入完成后执行

import('./users').then(m => {
  _updateConversationState = m.updateConversationState
  _invalidateConversationPreview = m.invalidateConversationPreview
  // 执行缓存的 patches
  for (const { conversationId, patch } of _pendingPatches) {
    _updateConversationState(conversationId, patch)
  }
  _pendingPatches = []
})

function updateConversationState(conversationId: Id, patch: ConversationPatch) {
  if (!_updateConversationState) {
    // 导入未完成，缓存 patch
    _pendingPatches.push({ conversationId, patch })
    return
  }
  _updateConversationState(conversationId, patch)
}

function invalidatePreview(conversationId: Id) {
  if (!_invalidateConversationPreview) return
  _invalidateConversationPreview(conversationId)
}

/**
 * 预清洗消息文本，避免每次渲染都调用 DOMPurify。
 * 在消息存入 store 时执行一次，而非每次 v-html 渲染时执行。
 */
function sanitizeText(text: string) {
  if (!text || typeof text !== 'string') return ''
  return DOMPurify.sanitize(text, { ALLOWED_TAGS: [] })
}

// ── seqId 持久化 ──────────────────────────────

function persistConversationSeqIds() {
  try {
    if (state.accountId) {
      localStorage.setItem(`charroom_seqids_${state.accountId}`, JSON.stringify(state.conversationSeqIds))
    }
  } catch (_) {}
}

export function getConversationSeqId(conversationId: Id) {
  const key = String(conversationId)
  return state.conversationSeqIds[key] || 0
}

export function setConversationSeqId(conversationId: Id, seqId: Id) {
  const key = String(conversationId)
  const current = state.conversationSeqIds[key] || 0
  if (BigInt(seqId) > BigInt(current)) {
    state.conversationSeqIds = {
      ...state.conversationSeqIds,
      [key]: seqId
    }
    persistConversationSeqIds()
  }
}

// ── 消息持久化 ────────────────────────────────

async function savePrivateMessage(message: ChatMessage) {
  if (!state.accountId) return
  const chatId = getPrivateConversationId(message)
  if (!chatId) return
  try {
    await dbAppendMessage(state.accountId, chatId, message)
  } catch (e) {
    console.warn('[MessageDB] 保存私聊消息失败:', e)
  }
}

async function saveGroupMessage(message: ChatMessage) {
  if (!state.accountId || !message.groupId) return
  const conversationId = `group:${message.groupId}`
  try {
    await dbAppendMessage(state.accountId, conversationId, message)
  } catch (e) {
    console.warn('[MessageDB] 保存群聊消息失败:', e)
  }
}

// ── 会话加载 + 分页 ───────────────────────────

// 分页游标：conversationId → 已加载的最旧消息 index（null=从头）；
// 配合 IndexedDB 游标分页，避免每次翻页全量读会话消息
const pageCursors = new Map<string, number | null>()

export async function loadConversation(id: Id | null, isGroup = false) {
  if (!id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    state.hasMoreMessages = true
    state.hasMoreGroupMessages = true
    return
  }
  state.selectedChatId = id
  if (state.accountId) {
    if (isGroup) {
      state.messages = []
      const conversationId = `group:${Math.abs(Number(id))}`
      const page = await dbGetMessagesPage(state.accountId, conversationId, null, PAGE_SIZE)
      state.groupMessages = page.messages
      state.hasMoreGroupMessages = page.hasMore
      pageCursors.set(conversationId, page.beforeIndex)
    } else {
      state.groupMessages = []
      const conversationId = String(id)
      const page = await dbGetMessagesPage(state.accountId, conversationId, null, PAGE_SIZE)
      state.messages = page.messages
      state.hasMoreMessages = page.hasMore
      pageCursors.set(conversationId, page.beforeIndex)
    }
  } else {
    state.messages = []
    state.groupMessages = []
    state.hasMoreMessages = true
    state.hasMoreGroupMessages = true
  }
}

export async function loadOlderMessages() {
  if (!state.selectedChatId || !state.accountId) return false

  const isGroup = Number(state.selectedChatId) < 0
  if (isGroup) {
    if (!state.hasMoreGroupMessages) return false
    const conversationId = `group:${Math.abs(Number(state.selectedChatId))}`
    const beforeIndex = pageCursors.get(conversationId)
    if (beforeIndex == null) {
      state.hasMoreGroupMessages = false
      return false
    }
    const page = await dbGetMessagesPage(state.accountId, conversationId, beforeIndex, PAGE_SIZE)
    if (page.messages.length === 0) {
      state.hasMoreGroupMessages = false
      return false
    }
    state.groupMessages = [...page.messages, ...state.groupMessages]
    state.hasMoreGroupMessages = page.hasMore
    pageCursors.set(conversationId, page.beforeIndex)
    return true
  } else {
    if (!state.hasMoreMessages) return false
    const conversationId = String(state.selectedChatId)
    const beforeIndex = pageCursors.get(conversationId)
    if (beforeIndex == null) {
      state.hasMoreMessages = false
      return false
    }
    const page = await dbGetMessagesPage(state.accountId, conversationId, beforeIndex, PAGE_SIZE)
    if (page.messages.length === 0) {
      state.hasMoreMessages = false
      return false
    }
    state.messages = [...page.messages, ...state.messages]
    state.hasMoreMessages = page.hasMore
    pageCursors.set(conversationId, page.beforeIndex)
    return true
  }
}

// ── 消息操作 ──────────────────────────────────

// 最近处理过的 messageId（防重复入列：后端未收到 ACK 会 30s×3 重发，
// 重连补拉/增量同步也可能与实时推送重复）
const seenMessageIds = new Set<string>()
function alreadySeen(messageId?: string | null): boolean {
  if (!messageId) return false
  if (seenMessageIds.has(messageId)) return true
  seenMessageIds.add(messageId)
  if (seenMessageIds.size > 500) {
    const oldest = seenMessageIds.values().next().value
    if (oldest !== undefined) seenMessageIds.delete(oldest)
  }
  return false
}

export function addMessage(m: ChatMessage) {
  if (alreadySeen(m.messageId)) return
  const chatId = getPrivateConversationId(m)
  if (!chatId) return
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  if (String(state.selectedChatId) === String(chatId)) {
    if (!m.messageId || !state.messages.some(row => row.messageId === m.messageId)) state.messages.push(sanitized)
  }
  if (String(m.user) !== 'you') {
    updateConversationState(chatId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      lastMessagePreview: conversationPreviewText(sanitized),
      unreadDelta: String(state.selectedChatId) === String(chatId) ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(chatId)
  } else {
    // 自己发出的消息同样更新联系人列表第二行的预览（不改变 incoming 排序时间）
    updateConversationState(chatId, { lastMessagePreview: conversationPreviewText(sanitized) })
  }
  if (m.seqId != null) {
    const partnerId = chatId
    const ids = [Number(state.accountId), Number(partnerId)].sort((a, b) => a - b)
    const convId = 'user:' + ids[0] + ':' + ids[1]
    setConversationSeqId(convId, m.seqId)
  }
  return savePrivateMessage(sanitized)
}

/** Bootstrap pages do not increase unread counts or advance incremental seq cursors. */
export async function restoreHistoryPage(accountId: string, messages: ChatMessage[]) {
  if (String(state.accountId) !== accountId) return
  const conversations = new Map<string, ChatMessage[]>()
  for (const message of messages) {
    const key = message.groupId ? `group:${message.groupId}` : getPrivateConversationId(message)
    if (!key) continue
    const batch = conversations.get(String(key)) || []
    batch.push({ ...message, text: sanitizeText(message.text || '') })
    conversations.set(String(key), batch)
  }
  for (const [conversationId, batch] of conversations) {
    if (String(state.accountId) !== accountId) return
    await dbMergeHistoryMessages(accountId, conversationId, batch)
    if (String(state.accountId) !== accountId) return
    batch.forEach(message => alreadySeen(message.messageId))
    const isGroup = conversationId.startsWith('group:')
    const uiId = isGroup ? `-${conversationId.slice(6)}` : conversationId
    invalidatePreview(uiId)
    if (String(state.selectedChatId) !== uiId) continue
    const page = await dbGetMessagesPage(accountId, conversationId, null, PAGE_SIZE)
    if (String(state.accountId) !== accountId || String(state.selectedChatId) !== uiId) continue
    pageCursors.set(conversationId, page.beforeIndex)
    if (isGroup) {
      state.groupMessages = page.messages
      state.hasMoreGroupMessages = page.hasMore
    } else {
      state.messages = page.messages
      state.hasMoreMessages = page.hasMore
    }
  }
}

export function upsertAgentStreamMessage(messageId: string, fullContent: string, done = false) {
  if (!messageId) return
  const existingIndex = state.messages.findIndex(m => m.messageId === messageId)
  if (existingIndex >= 0) {
    state.messages[existingIndex] = {
      ...state.messages[existingIndex],
      text: fullContent,
      done
    }
  } else {
    const agentMessage = {
      user: String(AGENT_USER_ID),
      text: fullContent,
      time: new Date().toISOString(),
      targetId: String(state.accountId),
      messageId,
      done
    }
    state.messages.push(agentMessage)
    savePrivateMessage(agentMessage)
  }
}

export function addGroupMessage(m: ChatMessage) {
  if (alreadySeen(m.messageId)) return
  const conversationId = `-${m.groupId}`
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  if (Math.abs(Number(state.selectedChatId)) === Number(m.groupId)) {
    if (!m.messageId || !state.groupMessages.some(row => row.messageId === m.messageId)) state.groupMessages.push(sanitized)
  }
  if (String(m.user) !== String(state.accountId)) {
    updateConversationState(conversationId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      lastMessagePreview: conversationPreviewText(sanitized),
      unreadDelta: Math.abs(Number(state.selectedChatId)) === Number(m.groupId) ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(conversationId)
  } else {
    updateConversationState(conversationId, { lastMessagePreview: conversationPreviewText(sanitized) })
  }
  if (m.seqId != null) {
    const convId = 'group:' + m.groupId
    setConversationSeqId(convId, m.seqId)
  }
  return saveGroupMessage(sanitized)
}

export function updateMessageStatus(messageId: string, status: import('../types/chat').MessageStatus) {
  for (let i = 0; i < state.messages.length; i++) {
    if (state.messages[i].messageId === messageId) {
      state.messages[i] = { ...state.messages[i], isSent: status }
      return
    }
  }
  for (let i = 0; i < state.groupMessages.length; i++) {
    if (state.groupMessages[i].messageId === messageId) {
      state.groupMessages[i] = { ...state.groupMessages[i], isSent: status }
      return
    }
  }
}

export function setSelectedChat(id: Id | null) {
  loadConversation(id, Number(id) < 0)
}

export function trimMessages(max: number) {
  if (state.messages.length > max) {
    state.messages = state.messages.slice(state.messages.length - max)
  }
}

export function trimGroupMessages(max: number) {
  if (state.groupMessages.length > max) {
    state.groupMessages = state.groupMessages.slice(state.groupMessages.length - max)
  }
}

export function deleteMessage(message: ChatMessage, isGroup = false) {
  const byId = message && message.messageId
    ? (m: ChatMessage) => m.messageId !== message.messageId
    : (m: ChatMessage) => m !== message
  if (isGroup) {
    state.groupMessages = state.groupMessages.filter(byId)
  } else {
    state.messages = state.messages.filter(byId)
  }
  // 同步删除 IndexedDB 中的持久化消息，避免刷新后复活
  if (state.accountId && message && message.messageId) {
    const chatId = isGroup
      ? `group:${message.groupId}`
      : getPrivateConversationId(message)
    if (chatId) {
      dbDeleteMessage(state.accountId, chatId, message.messageId).catch(e =>
        console.warn('[MessageDB] 删除消息失败:', e)
      )
    }
  }
}

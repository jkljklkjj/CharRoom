import { state, PAGE_SIZE } from './state'
import { getPrivateConversationId, loadPrivateConversation, loadGroupConversation,
         buildPrivateHistoryKey, buildGroupHistoryKey, normalizeTimeValue,
         getConversationKey } from './storage'
import DOMPurify from 'dompurify'

// 延迟导入 users.js 避免循环依赖
let _invalidateConversationPreview = null
function invalidatePreview(conversationId) {
  if (!_invalidateConversationPreview) {
    import('./users.js').then(m => { _invalidateConversationPreview = m.invalidateConversationPreview })
    return
  }
  _invalidateConversationPreview(conversationId)
}

/**
 * 预清洗消息文本，避免每次渲染都调用 DOMPurify。
 * 在消息存入 store 时执行一次，而非每次 v-html 渲染时执行。
 */
function sanitizeText(text) {
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

function restoreConversationSeqIds() {
  try {
    if (state.accountId) {
      const raw = localStorage.getItem(`charroom_seqids_${state.accountId}`)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (typeof parsed === 'object' && parsed !== null) {
          state.conversationSeqIds = parsed
        }
      }
    }
  } catch (_) {}
}

export function getConversationSeqId(conversationId) {
  const key = String(conversationId)
  return state.conversationSeqIds[key] || 0
}

export function setConversationSeqId(conversationId, seqId) {
  const key = String(conversationId)
  const current = state.conversationSeqIds[key] || 0
  if (seqId > current) {
    state.conversationSeqIds = {
      ...state.conversationSeqIds,
      [key]: seqId
    }
    persistConversationSeqIds()
  }
}

// ── 消息持久化 ────────────────────────────────

function savePrivateMessage(message) {
  if (!state.accountId) return
  const chatId = getPrivateConversationId(message)
  if (!chatId) return

  try {
    const raw = localStorage.getItem(buildPrivateHistoryKey(state.accountId, chatId))
    const parsed = raw ? JSON.parse(raw) : []
    const existing = Array.isArray(parsed) ? parsed : []
    existing.push(message)
    localStorage.setItem(buildPrivateHistoryKey(state.accountId, chatId), JSON.stringify(existing))
  } catch (e) {
    console.warn('Failed to save private message', e)
  }
}

function saveGroupMessage(message) {
  if (!state.accountId || !message.groupId) return

  try {
    const raw = localStorage.getItem(buildGroupHistoryKey(state.accountId, message.groupId))
    const parsed = raw ? JSON.parse(raw) : []
    const existing = Array.isArray(parsed) ? parsed : []
    existing.push(message)
    localStorage.setItem(buildGroupHistoryKey(state.accountId, message.groupId), JSON.stringify(existing))
  } catch (e) {
    console.warn('Failed to save group message', e)
  }
}

// ── 会话加载 + 分页 ───────────────────────────

export function loadConversation(id, isGroup = false) {
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
      const all = loadGroupConversation(state.accountId, Math.abs(Number(id)))
      state.groupMessages = all.slice(-PAGE_SIZE)
      state.hasMoreGroupMessages = all.length > PAGE_SIZE
    } else {
      state.groupMessages = []
      const all = loadPrivateConversation(state.accountId, id)
      state.messages = all.slice(-PAGE_SIZE)
      state.hasMoreMessages = all.length > PAGE_SIZE
    }
  } else {
    state.messages = []
    state.groupMessages = []
    state.hasMoreMessages = true
    state.hasMoreGroupMessages = true
  }
}

export function loadOlderMessages() {
  if (!state.selectedChatId || !state.accountId) return false

  const isGroup = Number(state.selectedChatId) < 0
  if (isGroup) {
    if (!state.hasMoreGroupMessages) return false
    const all = loadGroupConversation(state.accountId, Math.abs(Number(state.selectedChatId)))
    const currentCount = state.groupMessages.length
    const older = all.slice(Math.max(0, all.length - currentCount - PAGE_SIZE), all.length - currentCount)
    if (older.length === 0) {
      state.hasMoreGroupMessages = false
      return false
    }
    state.groupMessages = [...older, ...state.groupMessages]
    state.hasMoreGroupMessages = all.length - currentCount - older.length > 0
    return true
  } else {
    if (!state.hasMoreMessages) return false
    const all = loadPrivateConversation(state.accountId, state.selectedChatId)
    const currentCount = state.messages.length
    const older = all.slice(Math.max(0, all.length - currentCount - PAGE_SIZE), all.length - currentCount)
    if (older.length === 0) {
      state.hasMoreMessages = false
      return false
    }
    state.messages = [...older, ...state.messages]
    state.hasMoreMessages = all.length - currentCount - older.length > 0
    return true
  }
}

// ── 消息操作 ──────────────────────────────────

export function addMessage(m) {
  const chatId = getPrivateConversationId(m)
  if (!chatId) return
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  if (String(state.selectedChatId) === String(chatId)) {
    state.messages.push(sanitized)
  }
  if (String(m.user) !== 'you') {
    updateConversationState(chatId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      unreadDelta: String(state.selectedChatId) === String(chatId) ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(chatId)
  }
  savePrivateMessage(sanitized)
  if (m.seqId != null) {
    const partnerId = chatId
    const ids = [Number(state.accountId), Number(partnerId)].sort((a, b) => a - b)
    const convId = 'user:' + ids[0] + ':' + ids[1]
    setConversationSeqId(convId, m.seqId)
  }
}

export function upsertAgentStreamMessage(messageId, fullContent, done = false) {
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
      user: '900000001',
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

export function addGroupMessage(m) {
  const conversationId = `-${m.groupId}`
  // 预清洗文本，避免每次渲染调用 DOMPurify
  const sanitized = m.text != null ? { ...m, text: sanitizeText(m.text) } : m
  if (Math.abs(Number(state.selectedChatId)) === Number(m.groupId)) {
    state.groupMessages.push(sanitized)
  }
  if (String(m.user) !== String(state.accountId)) {
    updateConversationState(conversationId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      unreadDelta: Math.abs(Number(state.selectedChatId)) === Number(m.groupId) ? 0 : 1
    })
    // 清除预览缓存，下次 rebuild 时重新读取
    invalidatePreview(conversationId)
  }
  saveGroupMessage(sanitized)
  if (m.seqId != null) {
    const convId = 'group:' + m.groupId
    setConversationSeqId(convId, m.seqId)
  }
}

export function updateMessageStatus(messageId, status) {
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

export function setSelectedChat(id) {
  loadConversation(id, Number(id) < 0)
}

export function trimMessages(max) {
  if (state.messages.length > max) {
    state.messages = state.messages.slice(state.messages.length - max)
  }
}

export function trimGroupMessages(max) {
  if (state.groupMessages.length > max) {
    state.groupMessages = state.groupMessages.slice(state.groupMessages.length - max)
  }
}

export function deleteMessage(message, isGroup = false) {
  if (isGroup) {
    state.groupMessages = state.groupMessages.filter(m => m !== message)
  } else {
    state.messages = state.messages.filter(m => m !== message)
  }
}

// 从 users.js 延迟导入，避免循环依赖
let _updateConversationState = null
function updateConversationState(conversationId, patch) {
  if (!_updateConversationState) {
    // 动态导入避免循环依赖
    import('./users.js').then(m => { _updateConversationState = m.updateConversationState })
    return
  }
  _updateConversationState(conversationId, patch)
}

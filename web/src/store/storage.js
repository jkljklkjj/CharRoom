import { STORAGE_PREFIX } from './state'

export function sanitizeId(value) {
  return String(value || '').trim()
}

export function buildPrivateHistoryKey(accountId, chatId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_private_${sanitizeId(chatId)}`
}

export function buildGroupHistoryKey(accountId, groupId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_group_${sanitizeId(groupId)}`
}

export function buildAccountPrefix(accountId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_`
}

export function getConversationKey(conversationId) {
  return String(conversationId || '')
}

export function isGroupConversationId(conversationId) {
  return Number(conversationId) < 0
}

export function normalizeTimeValue(value) {
  if (value == null || value === '') return 0
  if (typeof value === 'number') return value
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

export function getPrivateConversationId(message) {
  if (!message) return null
  if (String(message.user) === 'you') {
    return sanitizeId(message.targetId)
  }
  return sanitizeId(message.user)
}

export function loadPrivateConversation(accountId, chatId) {
  if (!accountId || !chatId) return []
  const key = buildPrivateHistoryKey(accountId, chatId)
  const raw = localStorage.getItem(key)
  if (!raw) return []
  try {
    const items = JSON.parse(raw)
    return Array.isArray(items) ? items : []
  } catch (e) {
    console.warn('Failed to load private chat history', e)
    return []
  }
}

export function loadGroupConversation(accountId, groupId) {
  if (!accountId || !groupId) return []
  const key = buildGroupHistoryKey(accountId, groupId)
  const raw = localStorage.getItem(key)
  if (!raw) return []
  try {
    const items = JSON.parse(raw)
    return Array.isArray(items) ? items : []
  } catch (e) {
    console.warn('Failed to load group chat history', e)
    return []
  }
}

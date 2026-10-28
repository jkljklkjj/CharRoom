import type { Id, TimeValue, ChatMessage } from '../types/chat'
import {ConversationId} from '../utils/conversationId'
import { STORAGE_PREFIX } from './state'

export function sanitizeId(value: Id | null | undefined) {
  return String(value || '').trim()
}

export function buildPrivateHistoryKey(accountId: Id, chatId: Id) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_private_${sanitizeId(chatId)}`
}

export function buildGroupHistoryKey(accountId: Id, groupId: Id) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_group_${sanitizeId(groupId)}`
}

export function buildAccountPrefix(accountId: Id) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_`
}

export function getConversationKey(conversationId: Id | null) {
  return conversationId == null ? '' : ConversationId.parse(conversationId).key
}

export function isGroupConversationId(conversationId: Id | null) {
  return conversationId != null && ConversationId.parse(conversationId).isGroup
}

export function normalizeTimeValue(value: TimeValue | null | undefined) {
  if (value == null || value === '') return 0
  if (typeof value === 'number') return value
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

export function getPrivateConversationId(message: ChatMessage | null) {
  if (!message) return null
  if (String(message.user) === 'you') {
    return sanitizeId(message.targetId)
  }
  return sanitizeId(message.user)
}

export function loadPrivateConversation(accountId: Id, chatId: Id): ChatMessage[] {
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

export function loadGroupConversation(accountId: Id, groupId: Id): ChatMessage[] {
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

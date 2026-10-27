import type { Id, PendingRegister } from '../types/chat'
import { state } from './state'
import { buildAccountPrefix } from './storage'
import { rebuildConversationStates } from './users'
import { sessionEpoch } from '../services/sessionEpoch'

export function setToken(t: string) { state.token = t }
export function setRefreshToken(t: string) { state.refreshToken = t }
export function setAccountId(id: Id) {
  sessionEpoch.renew()
  if (String(id) !== state.accountId) {
    state.users = []
    state.groups = []
    state.selectedChatId = null
  }
  state.accountId = String(id)
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  state.conversationSeqIds = {}
  rebuildConversationStates()
  restoreConversationSeqIds()
}

export function setPendingRegister(obj: PendingRegister) { state.pendingRegister = obj }
export function clearPendingRegister() { state.pendingRegister = null }
export function setLoginValid(valid: boolean) { state.loginValid = valid }

export function clearAll() {
  sessionEpoch.renew()
  if (state.accountId) {
    const accountId = state.accountId
    const prefix = buildAccountPrefix(accountId)
    Object.keys(localStorage)
      .filter(key => key.startsWith(prefix))
      .forEach(key => localStorage.removeItem(key))
    try { localStorage.removeItem(`charroom_seqids_${state.accountId}`) } catch (_) {}
    // 清除 IndexedDB 消息数据
    import('../utils/messageDB').then(m => m.clearAllMessages(accountId)).catch(() => {})
  }
  state.users = []
  state.groups = []
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  state.conversationSeqIds = {}
}

// seqId 恢复（从 messages.ts 移过来的辅助函数）
function restoreConversationSeqIds() {
  try {
    if (state.accountId) {
      const raw = localStorage.getItem(`charroom_seqids_${state.accountId}`)
      if (raw) {
        const parsed = JSON.parse(raw)
        // Earlier clients advanced these values on ACK/hints, without applying history.
        // They are not trustworthy applied cursors. Reset once and merge history idempotently.
        if (parsed?.version === 2 && typeof parsed.cursors === 'object' && parsed.cursors !== null) {
          const cursors: Record<string, Id> = {}
          for (const [key, value] of Object.entries(parsed.cursors)) {
            if ((typeof value === 'string' && /^\d+$/.test(value)) ||
              (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0)) cursors[key] = value
          }
          state.conversationSeqIds = cursors
        }
      }
    }
  } catch (_) {}
}

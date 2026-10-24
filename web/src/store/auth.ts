import type { Id, PendingRegister } from '../types/chat'
import { state } from './state'
import { buildAccountPrefix } from './storage'
import { rebuildConversationStates } from './users'

export function setToken(t: string) { state.token = t }
export function setRefreshToken(t: string) { state.refreshToken = t }
export function setAccountId(id: Id) {
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
        if (typeof parsed === 'object' && parsed !== null) {
          state.conversationSeqIds = parsed
        }
      }
    }
  } catch (_) {}
}

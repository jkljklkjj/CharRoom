import type { Id, PendingRegister } from '../types/chat'
import { resetConversationViews } from './messages'
import { state } from './state'
import { buildAccountPrefix } from './storage'
import { clearOwnProfileCache } from '../utils/socialProfileCache'
import { rebuildConversationStates,clearConversationPreviewCache } from './users'
import { loadConversationPrefs, resetConversationPrefs } from './conversationPrefs'
import { sessionEpoch } from '../services/sessionEpoch'
import {socialViewCache} from '../services/socialViewCache'
import {clearDailyAnswerDraft} from '../utils/dailyAnswerDraft'

export function setToken(t: string) { state.token = t }
export function setRefreshToken(t: string) { state.refreshToken = t }
export function setAccountId(id: Id) {
  sessionEpoch.renew()
  socialViewCache.clear()
  resetConversationViews()
  clearConversationPreviewCache()
  if (String(id) !== state.accountId) {
    state.users = []
    state.groups = []
    state.selectedChatId = null
  }
  state.directoryPhase='loading'
  state.accountId = String(id)
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  state.conversationSeqIds = {}
  loadConversationPrefs(state.accountId)
  rebuildConversationStates()
}

export function setPendingRegister(obj: PendingRegister) { state.pendingRegister = obj }
export function clearPendingRegister() { state.pendingRegister = null }
export function setLoginValid(valid: boolean) { state.loginValid = valid }

export function clearAll() {
  sessionEpoch.renew()
  socialViewCache.clear()
  resetConversationViews()
  clearConversationPreviewCache()
  if (state.accountId) {
    const accountId = state.accountId
    const prefix = buildAccountPrefix(accountId)
    Object.keys(localStorage)
      .filter(key => key.startsWith(prefix))
      .forEach(key => localStorage.removeItem(key))
    try { localStorage.removeItem(`charroom_seqids_${state.accountId}`) } catch (_) {}
    clearOwnProfileCache(accountId)
    clearDailyAnswerDraft(String(accountId))
    // 清除 IndexedDB 消息数据
    import('../utils/messageDB').then(m => m.clearAllMessages(accountId)).catch(() => {})
  }
  state.users = []
  state.groups = []
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  state.conversationSeqIds = {}
  resetConversationPrefs()
}

import type { ChatState } from '../types/chat'
import { reactive } from 'vue'

export const STORAGE_PREFIX = 'charroom_chat_history_'
export const PAGE_SIZE = 50

export const state = reactive<ChatState>({
  users: [],
  groups: [],
  messages: [],
  groupMessages: [],
  conversationStates: {},
  conversationSeqIds: {},
  token: '',
  refreshToken: '',
  accountId: '',
  pendingRegister: null,
  selectedChatId: null,
  loginValid: false,
  hasMoreMessages: true,
  hasMoreGroupMessages: true
})

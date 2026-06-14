import { reactive, readonly } from 'vue'

const STORAGE_PREFIX = 'charroom_chat_history_'

const state = reactive({
  users: [],
  messages: [],
  groupMessages: [],
  conversationStates: {},
  token: '',
  refreshToken: '',
  accountId: '',
  pendingRegister: null,
  selectedChatId: null,
  loginValid: false // 标记token是否已经通过有效性验证
})

function sanitizeId(value) {
  return String(value || '').trim()
}

function buildPrivateHistoryKey(accountId, chatId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_private_${sanitizeId(chatId)}`
}

function buildGroupHistoryKey(accountId, groupId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_group_${sanitizeId(groupId)}`
}

function buildAccountPrefix(accountId) {
  return `${STORAGE_PREFIX}${sanitizeId(accountId)}_`
}

function getConversationKey(conversationId) {
  return String(conversationId || '')
}

function isGroupConversationId(conversationId) {
  return Number(conversationId) < 0
}

function normalizeTimeValue(value) {
  if (value == null || value === '') return 0
  if (typeof value === 'number') return value
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? 0 : parsed
}

function loadConversationPreview(accountId, conversationId) {
  const id = sanitizeId(conversationId)
  if (!accountId || !id) {
    return { lastIncomingMessageTime: 0, unreadCount: 0 }
  }

  const isGroup = isGroupConversationId(id)
  const items = isGroup
    ? loadGroupConversation(accountId, Math.abs(Number(id)))
    : loadPrivateConversation(accountId, id)

  let lastIncomingMessageTime = 0
  items.forEach(item => {
    const messageTime = normalizeTimeValue(item.time || item.timestamp)
    const isIncoming = isGroup
      ? String(item.user) !== String(accountId)
      : String(item.user) !== 'you'
    if (isIncoming) {
      lastIncomingMessageTime = Math.max(lastIncomingMessageTime, messageTime)
    }
  })

  return { lastIncomingMessageTime, unreadCount: 0 }
}

function rebuildConversationStates() {
  if (!state.accountId || !Array.isArray(state.users)) {
    state.conversationStates = {}
    return
  }

  const existingStates = state.conversationStates || {}
  const nextStates = {}
  state.users.forEach(user => {
    if (!user || user.id == null) return
    const preview = loadConversationPreview(state.accountId, user.id)
    const existing = existingStates[String(user.id)] || {}
    nextStates[String(user.id)] = {
      lastIncomingMessageTime: preview.lastIncomingMessageTime,
      unreadCount: existing.unreadCount || 0
    }
  })
  state.conversationStates = nextStates
}

function updateConversationState(conversationId, patch = {}) {
  const key = getConversationKey(conversationId)
  if (!key) return
  const current = state.conversationStates[key] || { lastIncomingMessageTime: 0, unreadCount: 0 }
  state.conversationStates = {
    ...state.conversationStates,
    [key]: {
      lastIncomingMessageTime: Math.max(current.lastIncomingMessageTime || 0, patch.lastIncomingMessageTime || 0),
      unreadCount: patch.unreadCount != null
        ? Math.max(0, patch.unreadCount)
        : Math.max(0, (current.unreadCount || 0) + (patch.unreadDelta || 0))
    }
  }
}

function clearConversationUnread(conversationId) {
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

function getPrivateConversationId(message) {
  if (!message) return null
  if (String(message.user) === 'you') {
    return sanitizeId(message.targetId)
  }
  return sanitizeId(message.user)
}


function loadPrivateConversation(accountId, chatId) {
  if (!accountId || !chatId) return []
  const key = buildPrivateHistoryKey(accountId, chatId)
  const raw = localStorage.getItem(key)
  if (!raw) return []
  try {
    const items = JSON.parse(raw)
    return Array.isArray(items) ? items : []
  } catch (e) {
    console.warn('加载私聊历史失败', e)
    return []
  }
}

function loadGroupConversation(accountId, groupId) {
  if (!accountId || !groupId) return []
  const key = buildGroupHistoryKey(accountId, groupId)
  const raw = localStorage.getItem(key)
  if (!raw) return []
  try {
    const items = JSON.parse(raw)
    return Array.isArray(items) ? items : []
  } catch (e) {
    console.warn('加载群聊历史失败', e)
    return []
  }
}

function loadHistory(accountId) {
  const id = accountId || state.accountId
  if (!id) return

  // 只清空当前缓存，不一次性加载所有会话
  state.messages = []
  state.groupMessages = []
}

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
    console.warn('保存私聊消息失败', e)
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
    console.warn('保存群聊消息失败', e)
  }
}

function loadConversation(id, isGroup = false) {
  console.log('🟢 loadConversation 调用, id=', id, 'isGroup=', isGroup)
  if (!id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    console.log('🟢 !id 分支, selectedChatId 重置为 null')
    return
  }
  state.selectedChatId = id
  clearConversationUnread(id)
  console.log('🟢 已设置 selectedChatId=', state.selectedChatId)
  if (state.accountId) {
    if (isGroup) {
      state.messages = []
      state.groupMessages = loadGroupConversation(state.accountId, Math.abs(Number(id)))
    } else {
      state.groupMessages = []
      state.messages = loadPrivateConversation(state.accountId, id)
    }
  } else {
    // accountId为空时，只设置选中ID，不加载历史消息
    state.messages = []
    state.groupMessages = []
  }
}

function setToken(t) { state.token = t }
function setRefreshToken(t) { state.refreshToken = t }
function setAccountId(id) {
  state.accountId = id
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  rebuildConversationStates()
}
function setUsers(list) {
  state.users = list
  rebuildConversationStates()
}
function addUser(u) {
  if (!state.users.some(x => x.id === u.id)) {
    state.users.push(u)
    rebuildConversationStates()
  }
}

/**
 * 更新用户在线状态
 */
function updateUserOnlineStatus(userId, online) {
  state.users = state.users.map(user => {
    if (user.id === userId) {
      return {
        ...user,
        online: online,
        status: online ? '在线' : '离线'
      }
    }
    return user
  })
}
function addMessage(m) {
  const chatId = getPrivateConversationId(m)
  if (!chatId) return
  if (String(state.selectedChatId) === String(chatId)) {
    state.messages.push(m)
  }
  if (String(m.user) !== 'you') {
    updateConversationState(chatId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      unreadDelta: String(state.selectedChatId) === String(chatId) ? 0 : 1
    })
  }
  savePrivateMessage(m)
}
function addGroupMessage(m) {
  const conversationId = `-${m.groupId}`
  if (Math.abs(Number(state.selectedChatId)) === Number(m.groupId)) {
    state.groupMessages.push(m)
  }
  if (String(m.user) !== String(state.accountId)) {
    updateConversationState(conversationId, {
      lastIncomingMessageTime: normalizeTimeValue(m.time || m.timestamp),
      unreadDelta: Math.abs(Number(state.selectedChatId)) === Number(m.groupId) ? 0 : 1
    })
  }
  saveGroupMessage(m)
}
function setSelectedChat(id) {
  loadConversation(id, Number(id) < 0)
  clearConversationUnread(id)
}
function clearAll() {
  if (state.accountId) {
    const prefix = buildAccountPrefix(state.accountId)
    Object.keys(localStorage)
      .filter(key => key.startsWith(prefix))
      .forEach(key => localStorage.removeItem(key))
  }
  state.users = []
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
}
function setPendingRegister(obj) { state.pendingRegister = obj }
function clearPendingRegister() { state.pendingRegister = null }
function setLoginValid(valid) { state.loginValid = valid }

export function useStore() {
  return {
    state: readonly(state),
    setToken,
    setRefreshToken,
    setAccountId,
    setUsers,
    addUser,
    addMessage,
    addGroupMessage,
    setSelectedChat,
    clearAll,
    setPendingRegister,
    clearPendingRegister,
    updateUserOnlineStatus,
    rebuildConversationStates,
    updateConversationState,
    clearConversationUnread,
    setLoginValid
  }
}

export default useStore()

import { reactive, readonly } from 'vue'

const STORAGE_PREFIX = 'charroom_chat_history_'

const state = reactive({
  users: [],
  messages: [],
  groupMessages: [],
  token: '',
  accountId: '',
  pendingRegister: null,
  selectedChatId: null
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
  if (!state.accountId || !id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    return
  }
  state.selectedChatId = id
  if (isGroup) {
    state.messages = []
    state.groupMessages = loadGroupConversation(state.accountId, id)
  } else {
    state.groupMessages = []
    state.messages = loadPrivateConversation(state.accountId, id)
  }
}

function setToken(t) { state.token = t }
function setAccountId(id) {
  state.accountId = id
  state.messages = []
  state.groupMessages = []
}
function setUsers(list) { state.users = list }
function addUser(u) { if (!state.users.some(x => x.id === u.id)) state.users.push(u) }
function addMessage(m) {
  const chatId = getPrivateConversationId(m)
  if (!chatId) return
  if (String(state.selectedChatId) === String(chatId)) {
    state.messages.push(m)
  }
  savePrivateMessage(m)
}
function addGroupMessage(m) {
  if (String(state.selectedChatId) === String(m.groupId)) {
    state.groupMessages.push(m)
  }
  saveGroupMessage(m)
}
function setSelectedChat(id) { loadConversation(id, Number(id) < 0) }
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
}
function setPendingRegister(obj) { state.pendingRegister = obj }
function clearPendingRegister() { state.pendingRegister = null }

export function useStore() {
  return {
    state: readonly(state),
    setToken,
    setAccountId,
    setUsers,
    addUser,
    addMessage,
    addGroupMessage,
    setSelectedChat,
    clearAll,
    setPendingRegister,
    clearPendingRegister
  }
}

export default useStore()

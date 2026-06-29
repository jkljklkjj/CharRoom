import { reactive, readonly } from 'vue'
import i18n from '../i18n'

const STORAGE_PREFIX = 'charroom_chat_history_'

const state = reactive({
  users: [],
  groups: [],
  messages: [],
  groupMessages: [],
  conversationStates: {},
  // 每个会话已同步到的 seqId（用于增量拉取）
  conversationSeqIds: {},
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

// ── seqId 持久化 ──────────────────────────────────────────

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
    console.warn('Failed to load private chat history', e)
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
    console.warn('Failed to load group chat history', e)
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

function loadConversation(id, isGroup = false) {
  console.log('loadConversation called, id=', id, 'isGroup=', isGroup)
  if (!id) {
    state.selectedChatId = null
    state.messages = []
    state.groupMessages = []
    console.log('id is falsy, selectedChatId reset to null')
    return
  }
  state.selectedChatId = id
  clearConversationUnread(id)
  console.log('selectedChatId set to', state.selectedChatId)
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
  state.conversationSeqIds = {}
  rebuildConversationStates()
  restoreConversationSeqIds()
}

// ── seqId 跟踪 ───────────────────────────────────────────

/**
 * 获取会话的已同步 seqId。
 * @param {string} conversationId
 * @returns {number}
 */
function getConversationSeqId(conversationId) {
  const key = String(conversationId)
  return state.conversationSeqIds[key] || 0
}

/**
 * 更新会话的已同步 seqId（取最大值，防止回退）。
 * @param {string} conversationId
 * @param {number} seqId
 */
function setConversationSeqId(conversationId, seqId) {
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
function setUsers(list) {
  state.users = list
  rebuildConversationStates()
}

function setGroups(list) {
  state.groups = list
}

/**
 * 增量合并用户列表。
 * 保留已有对象的引用，只更新变化的字段。
 * Vue 可以复用 DOM 节点，避免整个列表重建。
 */
function mergeUsers(incoming) {
  if (!Array.isArray(incoming)) return

  const existingById = new Map(state.users.map(u => [u.id, u]))
  let changed = false

  for (const user of incoming) {
    const existing = existingById.get(user.id)
    if (!existing) {
      // 新增用户
      existingById.set(user.id, { ...user })
      changed = true
    } else {
      // 只更新有变化的字段（保留引用）
      let modified = false
      if (existing.online !== user.online) { existing.online = user.online; modified = true }
      if (existing.username !== user.username) { existing.username = user.username; modified = true }
      if (existing.status !== user.status) { existing.status = user.status; modified = true }
      if (existing.avatarUrl !== user.avatarUrl) { existing.avatarUrl = user.avatarUrl; modified = true }
      if (existing.signature !== user.signature) { existing.signature = user.signature; modified = true }
      if (modified) changed = true
    }
  }

  // 检查是否有已删除的好友
  const incomingIds = new Set(incoming.map(u => u.id))
  for (const [id, _u] of existingById) {
    if (!incomingIds.has(id) && id > 0) { // 跳过 AI 助手等特殊用户
      existingById.delete(id)
      changed = true
    }
  }

  if (changed) {
    state.users = Array.from(existingById.values())
    // 懒惰重建，不阻塞渲染
    setTimeout(() => rebuildConversationStates(), 0)
  }
}

/**
 * 持久化好友列表到 localStorage（用于启动时快速恢复）。
 */
function cacheUsers(users) {
  try {
    if (state.accountId) {
      const key = `charroom_users_${state.accountId}`
      localStorage.setItem(key, JSON.stringify({
        version: Date.now(),
        users: users.map(u => ({
          id: u.id,
          username: u.username,
          online: u.online,
          status: u.status,
          avatarUrl: u.avatarUrl,
          signature: u.signature
        }))
      }))
    }
  } catch (_) { /* localStorage 满时静默失败 */ }
}

/**
 * 从 localStorage 恢复缓存的好友列表。
 */
function loadCachedUsers() {
  try {
    if (state.accountId) {
      const raw = localStorage.getItem(`charroom_users_${state.accountId}`)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (parsed && Array.isArray(parsed.users)) return parsed.users
      }
    }
  } catch (_) { /* ignore */ }
  return []
}
function addUser(u) {
  if (!state.users.some(x => x.id === u.id)) {
    state.users.push(u)
    rebuildConversationStates()
  }
}
function removeUser(id) {
  const idx = state.users.findIndex(u => u.id === id)
  if (idx === -1) return
  state.users.splice(idx, 1)
  // 清理该好友的会话状态和消息
  delete state.conversationStates[id]
  state.messages = state.messages.filter(m => m.user !== id.toString())
  // 如果当前选中的就是这个好友，取消选中
  if (state.selectedChatId === id) {
    loadConversation(null, false)
    clearConversationUnread(id)
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
        status: online ? i18n.global.t('sidebar.online') : i18n.global.t('sidebar.offline')
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
  // 如果消息带 seqId，更新会话的已同步游标
  if (m.seqId != null) {
    const partnerId = chatId
    const ids = [Number(state.accountId), Number(partnerId)].sort((a, b) => a - b)
    const convId = 'user:' + ids[0] + ':' + ids[1]
    setConversationSeqId(convId, m.seqId)
  }
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
  // 如果消息带 seqId，更新群聊会话的已同步游标
  if (m.seqId != null) {
    const convId = 'group:' + m.groupId
    setConversationSeqId(convId, m.seqId)
  }
}
function setSelectedChat(id) {
  loadConversation(id, Number(id) < 0)
  clearConversationUnread(id)
}
/**
 * 头像缓存（内存 + localStorage）。
 * key = avatarUrl, value = data:URL base64
 */
const avatarCache = new Map()

function getCachedAvatar(url) {
  if (!url) return null
  if (avatarCache.has(url)) return avatarCache.get(url)
  try {
    const key = `charroom_avatar_${btoa(url).slice(0, 40)}`
    const cached = localStorage.getItem(key)
    if (cached) {
      avatarCache.set(url, cached)
      return cached
    }
  } catch (_) {}
  return null
}

function setCachedAvatar(url, dataUrl) {
  if (!url || !dataUrl) return
  avatarCache.set(url, dataUrl)
  try {
    const key = `charroom_avatar_${btoa(url).slice(0, 40)}`
    localStorage.setItem(key, dataUrl)
  } catch (_) {}
}

/**
 * 更新某条消息的发送状态（0-8s=已发送 / 8-16s=发送中 / 16s+=失败）。
 * 支持私聊和群聊，按 messageId 匹配。
 */
function updateMessageStatus(messageId, status) {
  // 私聊
  for (let i = 0; i < state.messages.length; i++) {
    if (state.messages[i].messageId === messageId) {
      state.messages[i] = { ...state.messages[i], isSent: status }
      return
    }
  }
  // 群聊
  for (let i = 0; i < state.groupMessages.length; i++) {
    if (state.groupMessages[i].messageId === messageId) {
      state.groupMessages[i] = { ...state.groupMessages[i], isSent: status }
      return
    }
  }
}

function clearAll() {
  if (state.accountId) {
    const prefix = buildAccountPrefix(state.accountId)
    Object.keys(localStorage)
      .filter(key => key.startsWith(prefix))
      .forEach(key => localStorage.removeItem(key))
    // 清理 seqId
    try { localStorage.removeItem(`charroom_seqids_${state.accountId}`) } catch (_) {}
  }
  state.users = []
  state.groups = []
  state.messages = []
  state.groupMessages = []
  state.conversationStates = {}
  state.conversationSeqIds = {}
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
    setGroups,
    addUser,
    removeUser,
    addMessage,
    addGroupMessage,
    updateMessageStatus,
    setSelectedChat,
    clearAll,
    setPendingRegister,
    clearPendingRegister,
    updateUserOnlineStatus,
    rebuildConversationStates,
    updateConversationState,
    clearConversationUnread,
    setLoginValid,
    getConversationSeqId,
    setConversationSeqId,
    mergeUsers,
    cacheUsers,
    loadCachedUsers,
    getCachedAvatar,
    setCachedAvatar
  }
}

export default useStore()

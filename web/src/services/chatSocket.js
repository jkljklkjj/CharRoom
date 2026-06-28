import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'
import i18n from '../i18n'
import { createTransport, buildWebTransportUrl, isWebTransportSupported } from './transport/TransportFactory'

// ── 内部变量 ────────────────────────────────────

let transport = null          // ChatTransport 实例
let pendingQueue = []
let handlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }
let reconnectTimer = null
let heartbeatTimer = null
let heartbeatTimeoutTimer = null
let heartbeatInterval = 30000 // 30秒心跳
let heartbeatTimeout = 10000  // 心跳超时10秒
let currentReconnectDelay = 1000
let lastHeartbeatResponseTime = 0
let maxReconnectDelay = 30000
let isReconnecting = false
let stopReconnect = false
let currentUserId = null
let loggedIn = false
const MAX_QUEUE_SIZE = 1000
const MAX_MESSAGE_CACHE = 1000
// 消息去重：Map<messageId, timestamp>，带 TTL 自动过期
const messageCache = new Map()
const MESSAGE_TTL = 5 * 60 * 1000 // 5 分钟 TTL

// 优先级队列：多个队列按优先级处理
const PRIORITY_HIGH = 0   // ACK
const PRIORITY_NORMAL = 1 // 聊天消息
const PRIORITY_LOW = 2    // 心跳
const priorityQueues = { [PRIORITY_HIGH]: [], [PRIORITY_NORMAL]: [], [PRIORITY_LOW]: [] }

// 定期清理过期消息 ID
setInterval(() => {
  const now = Date.now()
  for (const [id, ts] of messageCache) {
    if (now - ts > MESSAGE_TTL) messageCache.delete(id)
  }
}, 60_000)

// ── 公共 API ────────────────────────────────────

/**
 * 建立聊天连接。
 *
 * @param {string} wsUrl - WebSocket 基础 URL（用于降级）
 * @param {string} token - 认证 token
 * @param {string|number} userId - 用户 ID
 * @param {Object} callbacks - 事件回调
 * @param {Function} [callbacks.onopen]
 * @param {Function} [callbacks.onmessage]
 * @param {Function} [callbacks.onclose]
 * @param {Function} [callbacks.onerror]
 * @param {Function} [callbacks.onAuthFailed]
 * @returns {Promise<ChatTransport>}
 */
export async function connect(hostname, port, token, userId, { onopen, onmessage, onclose, onerror, onAuthFailed } = {}) {
  console.log('🔌 尝试建立连接:', { hostname, port, hasToken: !!token, userId })

  if (!token || typeof token !== 'string' || token.trim() === '') {
    console.error('❌ 连接失败：token为空')
    stopReconnect = true
    if (onAuthFailed) onAuthFailed(i18n.global.t('error.tokenEmpty'))
    throw new Error(i18n.global.t('error.connectionFailed'))
  }

  if (!isWebTransportSupported()) {
    throw new Error(i18n.global.t('error.webTransportNotSupported'))
  }

  stopReconnect = false
  currentUserId = userId
  handlers = {
    onopen: onopen || handlers.onopen,
    onmessage: onmessage || handlers.onmessage,
    onclose: onclose || handlers.onclose,
    onerror: onerror || handlers.onerror,
    onAuthFailed: onAuthFailed || (() => {})
  }

  // 断开旧的连接
  if (transport) {
    transport.close()
    transport = null
  }

  // 创建 WebTransport 传输层
  transport = createTransport()

  // 设置事件回调
  transport.onopen = () => {
    console.log('✅ 连接成功')
    isReconnecting = false
    currentReconnectDelay = 1000

    // 发送登录消息
    sendLogin(token)
    // 启动心跳
    startHeartbeat()
    if (handlers.onopen) handlers.onopen()
  }

  transport.onmessage = (rawData) => {
    handleMessage(rawData)
  }

  transport.onclose = (event) => {
    console.log('❌ 连接关闭')
    stopHeartbeat()

    if (!stopReconnect && !isReconnecting) {
      scheduleReconnect(hostname, port, token, currentUserId)
    }

    if (handlers.onclose) handlers.onclose(event)
  }

  transport.onerror = (event) => {
    console.error('💥 传输错误:', event)
    if (handlers.onerror) handlers.onerror(event)
  }

  // 建立连接
  try {
    const finalUrl = buildWebTransportUrl(hostname, port)
    await transport.connect(finalUrl, token)
    return transport
  } catch (e) {
    console.error('❌ 连接失败:', e)
    throw e
  }
}

/**
 * 发送登录消息（通过传输层）。
 * @param {string} token
 */
/**
 * 获取或生成本地设备 ID（持久化到 localStorage）
 */
function getDeviceId() {
  const key = 'charroom_device_id'
  let id = localStorage.getItem(key)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
    localStorage.setItem(key, id)
  }
  return id
}

/** 判断当前设备类型 */
function getDeviceType() {
  // Web 端固定为 "web"；KMP 客户端（Android/iOS/Desktop）自行设值
  return 'web'
}

async function sendLogin(token) {
  try {
    const result = await sendWrapper({
      type: 'login',
      login: {
        token: token,
        deviceType: getDeviceType(),
        deviceId: getDeviceId()
      }
    })
    console.log('📤 登录消息已发送, 结果:', result)
  } catch (err) {
    console.error('❌ 发送登录消息失败:', err)
  }
}

/**
 * 发送 protobuf 编码的消息。
 * @param {Object} wrapperObj - 消息对象
 * @returns {Promise<boolean>}
 */
/**
 * 根据消息类型确定优先级。
 */
function getMessagePriority(wrapperObj) {
  switch (wrapperObj.type) {
    case 'ack': return PRIORITY_HIGH
    case 'chat':
    case 'groupChat': return PRIORITY_NORMAL
    case 'heartbeat': return PRIORITY_LOW
    default: return PRIORITY_NORMAL
  }
}

export async function sendWrapper(wrapperObj) {
  try {
    const buffer = await encodeMessage(wrapperObj)

    if (transport && transport.isConnected()) {
      return transport.send(buffer)
    }

    // 连接未建立，按优先级加入队列
    const priority = getMessagePriority(wrapperObj)
    const queue = priorityQueues[priority]
    const totalQueued = Object.values(priorityQueues).reduce((sum, q) => sum + q.length, 0)
    if (totalQueued < MAX_QUEUE_SIZE) {
      queue.push(buffer)
      console.log(`消息加入队列 (pri=${priority}), 总队列长度:`, totalQueued + 1)
    } else {
      if (priority <= PRIORITY_NORMAL) {
        // 高/中优先级踢掉最低优先级的消息
        const lowQueue = priorityQueues[PRIORITY_LOW]
        if (lowQueue.length > 0) {
          lowQueue.shift()
          queue.push(buffer)
          console.log(`消息入队 (pri=${priority}), 踢掉一条低优先级消息`)
        } else {
          console.warn('消息队列已满，丢弃高优先级消息')
        }
      } else {
        console.warn('消息队列已满，丢弃低优先级消息')
      }
    }
    return false
  } catch (e) {
    console.error('发送消息失败:', e)
    return false
  }
}

/**
 * 刷新消息队列（按优先级从高到低发送）。
 */
function flushQueue() {
  if (!loggedIn) return
  const total = Object.values(priorityQueues).reduce((sum, q) => sum + q.length, 0)
  if (total === 0) return

  console.log('发送队列中的消息，共', total, '条')
  for (const priority of [PRIORITY_HIGH, PRIORITY_NORMAL, PRIORITY_LOW]) {
    const queue = priorityQueues[priority]
    while (queue.length > 0) {
      const buffer = queue.shift()
      if (transport && transport.isConnected()) {
        transport.send(buffer)
      } else {
        // 发送失败时重新入队（但避免无限堆积）
        if (priority <= PRIORITY_NORMAL) {
          queue.unshift(buffer)
        }
        break
      }
    }
  }
}

/**
 * 发送 ACK 确认消息。
 * @param {string} messageId
 * @returns {Promise<boolean>}
 */
export async function sendAck(messageId) {
  if (!currentUserId || !messageId) return false
  return sendWrapper({
    type: 'ack',
    ack: { messageId: messageId }
  })
}

/**
 * 关闭连接。
 */
export function close() {
  stopReconnect = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  stopHeartbeat()

  if (transport) {
    transport.close()
    transport = null
  }

  pendingQueue = []
  Object.values(priorityQueues).forEach(q => q.length = 0)
  messageCache.clear()
  currentUserId = null
}

export function readyState() {
  return transport && transport.isConnected() ? 1 /* OPEN */ : 3 /* CLOSED */
}

export function isConnected() {
  return transport !== null && transport.isConnected()
}

// ── 心跳 ────────────────────────────────────────

function startHeartbeat() {
  stopHeartbeat()
  lastHeartbeatResponseTime = Date.now()

  heartbeatTimer = setInterval(() => {
    if (!transport || !transport.isConnected()) return

    const timeSinceLastResponse = Date.now() - lastHeartbeatResponseTime
    if (timeSinceLastResponse > heartbeatInterval + heartbeatTimeout) {
      console.log(`❤️ 心跳超时 (${timeSinceLastResponse}ms)，关闭连接`)
      transport.close()
      return
    }

    sendWrapper({
      type: 'heartbeat',
      heartbeat: { timestamp: Date.now() }
    }).catch(() => {
      console.log('心跳发送失败，关闭连接')
      if (transport) transport.close()
    })
  }, heartbeatInterval)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  if (heartbeatTimeoutTimer) {
    clearTimeout(heartbeatTimeoutTimer)
    heartbeatTimeoutTimer = null
  }
}

// ── 重连 ────────────────────────────────────────

function scheduleReconnect(hostname, port, token, userId) {
  if (reconnectTimer) clearTimeout(reconnectTimer)

  isReconnecting = true
  console.log(`将在 ${currentReconnectDelay}ms 后尝试重连...`)

  reconnectTimer = setTimeout(() => {
    if (!stopReconnect) {
      connect(hostname, port, token, userId, handlers).catch(() => {
        isReconnecting = false
      })
      // 指数退避 + jitter 随机化，避免重连风暴
      currentReconnectDelay = Math.min(
        currentReconnectDelay * 2 + Math.random() * 1000,
        maxReconnectDelay
      )
    }
  }, currentReconnectDelay)
}

// ── 消息处理 ────────────────────────────────────

/**
 * 处理收到的消息（二进制数据）。
 * @param {ArrayBuffer} rawData
 */
async function handleMessage(rawData) {
  let processedData = rawData

  // 解码 protobuf
  if (rawData instanceof ArrayBuffer || rawData instanceof Blob) {
    let buf
    if (rawData instanceof Blob) buf = await rawData.arrayBuffer()
    else buf = rawData
    try {
      processedData = await decodeMessage(buf)
    } catch (err) {
      console.warn('Protobuf 解码失败:', err.message)
      processedData = buf
    }
  }

  // 处理服务端响应
  if (processedData && typeof processedData === 'object') {
    // 任何来自服务端的成功消息都视为心跳有效
    lastHeartbeatResponseTime = Date.now()

    // 带 success 字段的响应（ResponseMessage / AckMessage）
    // protobuf 结构: { type, response: { success, message } }
    const isSuccess = processedData.success
      || (processedData.response && processedData.response.success)
    if (isSuccess !== undefined) {
      if (isSuccess) {
        loggedIn = true
        flushQueue()
      } else {
        const msg = (processedData.response ? processedData.response.message : processedData.message || '').toLowerCase()
        if (msg.includes('登录失败') || msg.includes('token无效') || msg.includes('token过期') || msg.includes('未授权') || msg.includes('unauthorized')) {
          console.log('🔑 认证失败，停止重连')
          stopReconnect = true
          handlers.onAuthFailed(msg)
        }
        return
      }
    }

    // 心跳/ACK 响应 — 更新心跳时间，不发新心跳（避免循环）
    if (processedData.type === 'heartbeat' || processedData.type === 'ack'
        || (processedData.heartbeat && typeof processedData.heartbeat === 'object')) {
      lastHeartbeatResponseTime = Date.now()
      return
    }

    // 用户在线状态更新
    if (processedData.clientId !== undefined && processedData.online !== undefined) {
      // 传给上层
    }

    // 新消息通知
    if (processedData.type === 'chat' && processedData.chat) {
      showNotification(processedData.chat)
    } else if (processedData.type === 'group_chat' && processedData.groupChat) {
      showNotification(processedData.groupChat, true)
    }
  }

  // 消息去重和ACK
  if (processedData && typeof processedData === 'object') {
    let messageId = null
    let isChatOrGroupMessage = false

    if (processedData.chat && processedData.chat.messageId) {
      messageId = processedData.chat.messageId
      isChatOrGroupMessage = true
    } else if (processedData.groupChat && processedData.groupChat.messageId) {
      messageId = processedData.groupChat.messageId
      isChatOrGroupMessage = true
    } else if (processedData.agentChat && processedData.agentChat.messageId) {
      messageId = processedData.agentChat.messageId
      isChatOrGroupMessage = true
    }

    if (messageId) {
      if (messageCache.has(messageId)) {
        console.log('收到重复消息，忽略:', messageId)
        return
      }
      messageCache.set(messageId, Date.now())
      if (messageCache.size > MAX_MESSAGE_CACHE) {
        const oldest = messageCache.entries().next().value
        if (oldest) messageCache.delete(oldest[0])
      }

      if (isChatOrGroupMessage) {
        sendAck(messageId).catch(e => console.warn("sendAck failed:", e))
      }
    }
  }

  if (handlers.onmessage) handlers.onmessage(processedData)
}

// ── XSS 防护 ────────────────────────────────────

export function sanitizeMessage(content) {
  return DOMPurify.sanitize(content, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'a', 'code', 'pre', 'br'],
    ALLOWED_ATTR: ['href', 'title', 'target', 'class'],
    ALLOW_UNKNOWN_PROTOCOLS: false,
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover']
  })
}

// ── 通知 ────────────────────────────────────────

function showNotification(message, isGroup = false) {
  if (document.visibilityState === 'visible') return
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') {
    Notification.requestPermission()
    return
  }

  const userId = currentUserId ? String(currentUserId) : ''
  const senderId = String(message.userId || '')
  if (senderId === userId) return

  const title = i18n.global.t(isGroup ? 'notification.groupMessage' : 'notification.newMessage')
  const body = message.content || i18n.global.t('notification.body')

  const notification = new Notification(title, {
    body,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-192x192.png',
    tag: isGroup ? `group-${message.targetClientId}` : `user-${senderId}`,
    renotify: true,
    silent: false
  })

  // 3 秒后自动关闭通知
  setTimeout(() => notification.close(), 3000)

  notification.onclick = () => {
    window.focus()
    notification.close()
  }
}

// 页面加载时申请通知权限
if ('Notification' in window && Notification.permission === 'default') {
  document.addEventListener('click', function requestPermission() {
    Notification.requestPermission()
    document.removeEventListener('click', requestPermission)
  }, { once: true })
}

export default { connect, sendWrapper, sendAck, close, readyState, isConnected, sanitizeMessage }

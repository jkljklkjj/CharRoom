import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'
import { createTransport, configureTransport, buildWebTransportUrl, buildWebSocketUrl, isWebTransportSupported } from './transport/TransportFactory'

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
let transportType = 'ws'      // 'ws' | 'wt' | 'auto'
const MAX_QUEUE_SIZE = 1000
const MAX_MESSAGE_CACHE = 1000
const receivedMessageIds = new Set()
const messageIdQueue = []

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
export async function connect(wsUrl, token, userId, { onopen, onmessage, onclose, onerror, onAuthFailed } = {}) {
  console.log('🔌 尝试建立连接:', { wsUrl, hasToken: !!token, userId })

  if (!token || typeof token !== 'string' || token.trim() === '') {
    console.error('❌ 连接失败：token为空')
    stopReconnect = true
    if (onAuthFailed) onAuthFailed('token不能为空')
    throw new Error('聊天连接失败：认证凭证为空')
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

  // 从 wsUrl 中提取 host 和 port
  const urlObj = tryParseUrl(wsUrl)
  const host = urlObj ? urlObj.hostname : 'chatlite.xin'
  const port = urlObj ? (urlObj.port || '443') : '443'

  // 检测浏览器 WebTransport 支持情况
  console.log(`🔍 WebTransport 支持: ${isWebTransportSupported() ? '✅ 支持' : '❌ 不支持'}`)

  // 创建合适的传输层
  transport = createTransport({
    forceWebSocket: transportType === 'ws'
  })

  // 设置事件回调
  transport.onopen = () => {
    console.log('✅ 连接成功，准备登录')
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
      scheduleReconnect(wsUrl, token, currentUserId)
    }

    if (handlers.onclose) handlers.onclose(event)
  }

  transport.onerror = (event) => {
    console.error('💥 传输错误:', event)
    if (handlers.onerror) handlers.onerror(event)
  }

  // 建立连接
  try {
    const finalUrl = transportType === 'wt' || (transportType === 'auto' && isWebTransportSupported())
      // WebTransport 走 quic.chatlite.xin:443 直连（标准 HTTPS 端口）
      ? buildWebTransportUrl('quic.chatlite.xin', 443)
      : wsUrl

    await transport.connect(finalUrl, token)
    return transport
  } catch (e) {
    console.error('❌ 连接失败:', e)
    // 如果 WebTransport 失败，自动降级到 WebSocket
    if (transportType === 'auto' && isWebTransportSupported()) {
      console.log('🔄 WebTransport 连接失败，降级到 WebSocket')
      transportType = 'ws'
      return connect(wsUrl, token, userId, { onopen, onmessage, onclose, onerror, onAuthFailed })
    }
    throw e
  }
}

/**
 * 发送登录消息（通过传输层）。
 * @param {string} token
 */
async function sendLogin(token) {
  try {
    const result = await sendWrapper({
      type: 'login',
      login: { token: token }
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
export async function sendWrapper(wrapperObj) {
  try {
    const buffer = await encodeMessage(wrapperObj)

    if (transport && transport.isConnected()) {
      return transport.send(buffer)
    }

    // 连接未建立，加入队列
    if (pendingQueue.length < MAX_QUEUE_SIZE) {
      pendingQueue.push(buffer)
      console.log('消息加入队列，当前队列长度:', pendingQueue.length)
    } else {
      console.warn('消息队列已满，丢弃消息')
    }
    return false
  } catch (e) {
    console.error('发送消息失败:', e)
    return false
  }
}

/**
 * 刷新消息队列。
 */
function flushQueue() {
  if (pendingQueue.length === 0) return

  console.log('发送队列中的消息，共', pendingQueue.length, '条')
  const queue = [...pendingQueue]
  pendingQueue = []

  queue.forEach(buffer => {
    if (transport && transport.isConnected()) {
      transport.send(buffer)
    } else {
      if (pendingQueue.length < MAX_QUEUE_SIZE) {
        pendingQueue.push(buffer)
      }
    }
  })
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
  receivedMessageIds.clear()
  messageIdQueue.length = 0
  currentUserId = null
}

export function readyState() {
  return transport && transport.isConnected() ? 1 /* OPEN */ : 3 /* CLOSED */
}

export function isConnected() {
  return transport !== null && transport.isConnected()
}

/**
 * 设置传输类型偏好。
 * @param {'auto'|'wt'|'ws'} type
 */
export function setTransportType(type) {
  transportType = type
}

/**
 * 获取当前传输类型。
 * @returns {string}
 */
export function getTransportType() {
  if (!transport) return 'none'
  return transport.constructor.name === 'WebTransportTransport' ? 'wt' : 'ws'
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

function scheduleReconnect(wsUrl, token, userId) {
  if (reconnectTimer) clearTimeout(reconnectTimer)

  isReconnecting = true
  console.log(`将在 ${currentReconnectDelay}ms 后尝试重连...`)

  reconnectTimer = setTimeout(() => {
    if (!stopReconnect) {
      connect(wsUrl, token, userId, handlers)
      currentReconnectDelay = Math.min(currentReconnectDelay * 2, maxReconnectDelay)
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
    } else if (processedData.type === 'groupChat' && processedData.groupChat) {
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
      if (receivedMessageIds.has(messageId)) {
        console.log('收到重复消息，忽略:', messageId)
        return
      }
      receivedMessageIds.add(messageId)
      messageIdQueue.push(messageId)
      if (receivedMessageIds.size > MAX_MESSAGE_CACHE) {
        const oldestId = messageIdQueue.shift()
        receivedMessageIds.delete(oldestId)
      }

      if (isChatOrGroupMessage) {
        sendAck(messageId).catch(() => {})
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

  const title = isGroup ? '群聊消息' : '新消息'
  const body = message.content || '收到一条新消息'

  const notification = new Notification(title, {
    body,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-192x192.png',
    tag: isGroup ? `group-${message.targetClientId}` : `user-${senderId}`,
    renotify: true,
    silent: false
  })

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

// ── 工具 ────────────────────────────────────────

function tryParseUrl(url) {
  try {
    return new URL(url)
  } catch {
    return null
  }
}

export default { connect, sendWrapper, sendAck, close, readyState, isConnected, sanitizeMessage, setTransportType, getTransportType }

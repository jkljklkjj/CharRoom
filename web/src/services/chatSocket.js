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
let ackCheckTimer = null      // ACK 超时检查定时器
let cacheCleanupTimer = null  // 消息去重缓存清理定时器
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

// 乐观 UI：待确认消息跟踪 Map<messageId, { sendTime, warned, failed }>
const pendingAcks = new Map()
const ACK_CONFIRM_MS = 8000   // 0-8s: 已发送（乐观）
const ACK_TIMEOUT_MS = 16000  // 8-16s: 发送中 → 16s+: 失败

// 优先级队列：多个队列按优先级处理
const PRIORITY_HIGH = 0   // ACK
const PRIORITY_NORMAL = 1 // 聊天消息
const PRIORITY_LOW = 2    // 心跳
const priorityQueues = { [PRIORITY_HIGH]: [], [PRIORITY_NORMAL]: [], [PRIORITY_LOW]: [] }

// ── Store 引用（依赖注入，替代 window.__chatStore） ──

let storeRef = null

/**
 * 注入 store 引用，chatSocket 内部需要读写 store 时使用。
 * 应在 App 初始化时调用一次。
 */
export function setStore(store) {
  storeRef = store
}

// ── 定时器生命周期管理 ──

function startTimers() {
  stopTimers()

  // 每秒检查待确认消息，更新状态
  ackCheckTimer = setInterval(() => {
    if (!storeRef) return
    const now = Date.now()
    for (const [msgId, info] of pendingAcks) {
      const age = now - info.sendTime
      if (age >= ACK_TIMEOUT_MS && !info.failed) {
        info.failed = true
        storeRef.updateMessageStatus(msgId, 'failed')
      } else if (age >= ACK_CONFIRM_MS && !info.warned) {
        info.warned = true
        storeRef.updateMessageStatus(msgId, 'sending')
      }
    }
  }, 1000)

  // 每分钟清理过期消息 ID
  cacheCleanupTimer = setInterval(() => {
    const now = Date.now()
    for (const [id, ts] of messageCache) {
      if (now - ts > MESSAGE_TTL) messageCache.delete(id)
    }
  }, 60_000)
}

function stopTimers() {
  if (ackCheckTimer) { clearInterval(ackCheckTimer); ackCheckTimer = null }
  if (cacheCleanupTimer) { clearInterval(cacheCleanupTimer); cacheCleanupTimer = null }
}

// ── 公共 API ────────────────────────────────────

/**
 * 建立聊天连接。
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

    // 启动定时器（连接生命周期内）
    startTimers()

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
 */
export async function sendWrapper(wrapperObj) {
  if (!transport || !transport.isConnected()) {
    // 未连接时加入队列
    if (pendingQueue.length < MAX_QUEUE_SIZE) {
      const buffer = await encodeMessage(wrapperObj)
      pendingQueue.push({ buffer, wrapperObj })
    }
    return false
  }

  const buffer = await encodeMessage(wrapperObj)

  // 消息去重检查
  const msgId = wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId
  if (msgId) {
    if (messageCache.has(msgId)) return false
    messageCache.set(msgId, Date.now())
  }

  // 确定流类型和会话 ID
  const streamType = getStreamType(wrapperObj)
  const conversationId = resolveConversationId(wrapperObj)

  const sent = transport.send(buffer, { streamType, conversationId })

  // 乐观 UI：跟踪待确认消息
  if (sent && msgId && wrapperObj.type === 'chat') {
    pendingAcks.set(msgId, { sendTime: Date.now(), warned: false, failed: false })
    if (storeRef) storeRef.updateMessageStatus(msgId, 'optimistic')
  }

  return sent
}

/**
 * 根据消息类型确定流类型。
 */
function getStreamType(wrapperObj) {
  const type = wrapperObj.type
  if (type === 'ack' || type === 'heartbeat' || type === 'login' || type === 'logout') {
    return 'control'
  }
  if (type === 'chat' || type === 'group_chat' || type === 'agent_chat') {
    return 'chat'
  }
  return 'control'
}

/**
 * 从消息对象中解析 conversationId。
 */
function resolveConversationId(wrapperObj) {
  if (wrapperObj.chat) {
    const target = wrapperObj.chat.targetClientId || ''
    const sender = wrapperObj.chat.userId || currentUserId || ''
    return `${sender}:${target}`
  }
  if (wrapperObj.groupChat) {
    return `group:${wrapperObj.groupChat.targetClientId || ''}`
  }
  return ''
}

function flushQueue() {
  const total = Object.values(priorityQueues).reduce((sum, q) => sum + q.length, 0)
  if (total === 0) return

  console.log('发送队列中的消息，共', total, '条')
  for (const priority of [PRIORITY_HIGH, PRIORITY_NORMAL, PRIORITY_LOW]) {
    const queue = priorityQueues[priority]
    while (queue.length > 0) {
      const entry = queue.shift()
      if (transport && transport.isConnected()) {
        transport.send(entry.buffer, entry.streamOptions)
      } else {
        if (priority <= PRIORITY_NORMAL) {
          queue.unshift(entry)
        }
        break
      }
    }
  }
}

/**
 * 发送 ACK 确认消息。
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
  stopTimers()

  if (transport) {
    transport.close()
    transport = null
  }

  pendingQueue = []
  Object.values(priorityQueues).forEach(q => q.length = 0)
  messageCache.clear()
  pendingAcks.clear()
  currentUserId = null
  loggedIn = false
}

export function readyState() {
  return transport && transport.isConnected() ? 1 /* OPEN */ : 3 /* CLOSED */
}

// ── 心跳 ────────────────────────────────────────

function startHeartbeat() {
  stopHeartbeat()

  function tick() {
    if (!transport || !transport.isConnected()) return

    // 心跳超时检查
    if (lastHeartbeatResponseTime > 0 && Date.now() - lastHeartbeatResponseTime > heartbeatInterval + heartbeatTimeout) {
      console.warn('心跳超时，关闭连接')
      if (transport) transport.close()
      return
    }

    lastHbSendTime = Date.now()
    sendWrapper({
      type: 'heartbeat',
      heartbeat: { timestamp: Date.now() }
    }).catch(e => {
      console.warn('心跳发送失败:', e)
      if (transport) transport.close()
    })
  }

  heartbeatTimer = setInterval(tick, heartbeatInterval)
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
      connect(hostname, port, token, userId, handlers).catch(e => {
        console.warn('重连失败:', e)
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
    // 任何来自服务端的成功消息都视为心跳有效，同时记录 RTT
    lastHeartbeatResponseTime = Date.now()
    recordRtt()

    // 带 success 字段的响应（ResponseMessage / AckMessage）
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

    // 心跳/ACK 响应
    if (processedData.type === 'heartbeat'
        || (processedData.heartbeat && typeof processedData.heartbeat === 'object')) {
      lastHeartbeatResponseTime = Date.now()
      return
    }
    // ACK 确认：更新消息状态 + seqId 游标
    if (processedData.type === 'ack') {
      lastHeartbeatResponseTime = Date.now()
      const ack = processedData.ack || processedData
      const ackedMsgId = ack.messageId
      if (ackedMsgId && pendingAcks.has(ackedMsgId)) {
        pendingAcks.delete(ackedMsgId)
        if (storeRef) storeRef.updateMessageStatus(ackedMsgId, 'sent')
      }
      // 更新 seqId 游标（用于增量同步断点续拉）
      if (ack.seqId != null && ack.conversationId && storeRef) {
        storeRef.setConversationSeqId(ack.conversationId, ack.seqId)
      }
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
      showNotification(processedData.groupChat)
    }

    // 同步响应
    if (processedData.type === 'response' && processedData.response) {
      // 传给上层
    }
  }

  // 传给上层回调
  if (handlers.onmessage) {
    handlers.onmessage(processedData)
  }
}

// ── RTT 记录 ────────────────────────────────────

let lastHbSendTime = 0
let rttSamples = []
const MAX_RTT_SAMPLES = 10

function recordRtt() {
  if (lastHbSendTime > 0) {
    const rtt = Date.now() - lastHbSendTime
    rttSamples.push(rtt)
    if (rttSamples.length > MAX_RTT_SAMPLES) rttSamples.shift()
  }
}

/**
 * 获取平均 RTT（毫秒）。
 */
export function getAverageRtt() {
  if (rttSamples.length === 0) return 0
  return Math.round(rttSamples.reduce((a, b) => a + b, 0) / rttSamples.length)
}

// ── 浏览器通知 ──────────────────────────────────

function showNotification(msg) {
  if (document.visibilityState === 'visible') return
  if (!('Notification' in window) || Notification.permission !== 'granted') return

  const title = msg.senderName || msg.senderId || '新消息'
  const body = msg.content || '[图片或其他内容]'
  try {
    new Notification(DOMPurify.sanitize(title), {
      body: DOMPurify.sanitize(body),
      icon: '/favicon.ico'
    })
  } catch (_) { /* ignore notification errors */ }
}

export default {
  connect,
  close,
  sendWrapper,
  sendAck,
  readyState,
  getAverageRtt,
  setStore
}

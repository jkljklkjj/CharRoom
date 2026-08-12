import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'
import i18n from '../i18n'
import { createTransport, buildWebTransportUrl, isWebTransportSupported } from './transport/TransportFactory'
import { saveToQueue, getAllPending, removeMessage, incrementRetry, MAX_RETRIES } from './offlineQueue'
import { getDeviceId, getDeviceType } from '../utils/device'

// ── 消息类型（与 message.proto MessageWrapperType 枚举值一致）────
export const MSG_TYPE = {
  LOGIN: 1, LOGOUT: 2, CHAT: 3, GROUP_CHAT: 4, AGENT_CHAT: 5,
  AGENT_CHAT_STREAM: 6, CHECK: 7, HEARTBEAT: 8, ACK: 9,
  RESPONSE: 10, STREAM_INIT_ACK: 11, SYNC_HINT: 12, FRIEND_ACCEPTED: 13
}

// ── 内部变量 ────────────────────────────────────

let transport = null          // ChatTransport 实例
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
let stopReconnect = false
let currentUserId = null
const MAX_MESSAGE_CACHE = 1000
// 消息去重：Map<messageId, timestamp>，带 TTL 自动过期
const messageCache = new Map()
const MESSAGE_TTL = 5 * 60 * 1000 // 5 分钟 TTL

// 乐观 UI：待确认消息跟踪 Map<messageId, { sendTime, warned, failed }>
const pendingAcks = new Map()
const ACK_TIMEOUT_MS = 16000  // 8-16s: 发送中 → 16s+: 失败

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

  // 每秒检查待确认消息，超时标记为失败
  ackCheckTimer = setInterval(() => {
    if (!storeRef) return
    const now = Date.now()
    for (const [msgId, info] of pendingAcks) {
      if (now - info.sendTime >= ACK_TIMEOUT_MS && !info.failed) {
        info.failed = true
        storeRef.updateMessageStatus(msgId, 'failed')
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
export async function connect(hostname, port, token, userId, { onopen, onmessage, onclose, onerror, onAuthFailed, onFriendAccepted } = {}) {
  console.debug('🔌 尝试建立连接:', { hostname, port, hasToken: !!token, userId })

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
    onAuthFailed: onAuthFailed || (() => {}),
    onFriendAccepted: onFriendAccepted || (() => {})
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
    console.debug('✅ 连接成功')
    currentReconnectDelay = 1000

    // 启动定时器（连接生命周期内）
    startTimers()

    // 发送登录消息
    sendLogin(token)

    // 刷新 IndexedDB 离线队列
    flushOfflineQueue()
    // 启动心跳
    startHeartbeat()
    if (handlers.onopen) handlers.onopen()
  }

  transport.onmessage = (rawData) => {
    handleMessage(rawData)
  }

  transport.onclose = (event) => {
    console.debug('❌ 连接关闭')
    stopHeartbeat()

    if (!stopReconnect) {
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

async function sendLogin(token) {
  try {
    const result = await sendWrapper({
      type: MSG_TYPE.LOGIN,
      login: {
        token: token,
        deviceType: getDeviceType(),
        deviceId: getDeviceId()
      }
    })
    console.debug('📤 登录消息已发送, 结果:', result)
  } catch (err) {
    console.error('❌ 发送登录消息失败:', err)
  }
}

/**
 * 发送 protobuf 编码的消息。
 */
export async function sendWrapper(wrapperObj) {
  if (!transport || !transport.isConnected()) {
    // 未连接时：持久化到 IndexedDB 离线队列，重连后由 flushOfflineQueue 重发
    if (wrapperObj.type === MSG_TYPE.CHAT || wrapperObj.type === MSG_TYPE.GROUP_CHAT) {
      const buffer = await encodeMessage(wrapperObj)
      saveToQueue(wrapperObj, buffer, {}).catch(e =>
        console.warn('[OfflineQueue] 保存到 IndexedDB 失败:', e)
      )
    }
    return false
  }

  const buffer = await encodeMessage(wrapperObj)

  // 消息去重检查
  const msgId = wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId
  if (msgId) {
    if (messageCache.has(msgId)) return false
    // 限制缓存大小
    if (messageCache.size >= MAX_MESSAGE_CACHE) {
      const firstKey = messageCache.keys().next().value
      messageCache.delete(firstKey)
    }
    messageCache.set(msgId, Date.now())
  }

  // 确定流类型和会话 ID
  const streamType = getStreamType(wrapperObj)
  const conversationId = resolveConversationId(wrapperObj)

  const sent = transport.send(buffer, { streamType, conversationId })

  // 乐观 UI：跟踪待确认消息（私聊 + 群聊）
  if (sent && msgId && (wrapperObj.type === MSG_TYPE.CHAT || wrapperObj.type === MSG_TYPE.GROUP_CHAT)) {
    pendingAcks.set(msgId, { sendTime: Date.now(), warned: false, failed: false })
    // 发送成功，从 IndexedDB 离线队列中移除
    removeMessage(msgId).catch(e => console.warn('[OfflineQueue] 移除消息失败:', e))
  }

  return sent
}

/**
 * 根据消息类型确定流类型。
 */
function getStreamType(wrapperObj) {
  const type = wrapperObj.type
  if (type === MSG_TYPE.ACK || type === MSG_TYPE.HEARTBEAT || type === MSG_TYPE.LOGIN || type === MSG_TYPE.LOGOUT) {
    return 'control'
  }
  if (type === MSG_TYPE.CHAT || type === MSG_TYPE.GROUP_CHAT || type === MSG_TYPE.AGENT_CHAT) {
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

/**
 * 刷新 IndexedDB 离线队列：网络恢复后自动重发离线消息。
 */
async function flushOfflineQueue() {
  try {
    const pending = await getAllPending()
    if (pending.length === 0) return

    console.debug(`[OfflineQueue] 发送离线队列中的 ${pending.length} 条消息`)
    for (const entry of pending) {
      // 超过最大重试次数直接丢弃，避免无限重发
      if (entry.retryCount >= MAX_RETRIES) {
        console.warn(`[OfflineQueue] 丢弃超出重试上限的消息: ${entry.messageId} (retryCount=${entry.retryCount})`)
        await removeMessage(entry.messageId)
        continue
      }
      try {
        // 兼容旧版普通数组条目（迁移前 Array.from 存储）与新 Uint8Array 条目
        const u8 = entry.buffer instanceof Uint8Array ? entry.buffer : new Uint8Array(entry.buffer)
        const buffer = u8.buffer.slice(u8.byteOffset, u8.byteOffset + u8.byteLength)
        const streamType = entry.opts?.streamType || getStreamType(entry.wrapperObj)
        const conversationId = entry.opts?.conversationId || resolveConversationId(entry.wrapperObj)

        if (transport && transport.isConnected()) {
          transport.send(buffer, { streamType, conversationId })
          await removeMessage(entry.messageId)
        } else {
          break // 连接断开，停止发送
        }
      } catch (e) {
        console.warn('[OfflineQueue] 发送失败:', entry.messageId, e)
        await incrementRetry(entry.messageId)
      }
    }
  } catch (e) {
    console.warn('[OfflineQueue] flushOfflineQueue 异常:', e)
  }
}

/**
 * 发送 ACK 确认消息。
 */
export async function sendAck(messageId) {
  if (!currentUserId || !messageId) return false
  return sendWrapper({
    type: MSG_TYPE.ACK,
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

  pendingAcks.clear()
  currentUserId = null
}

export function readyState() {
  return transport && transport.isConnected() ? 1 /* OPEN */ : 3 /* CLOSED */
}

// ── 心跳 ────────────────────────────────────────

function startHeartbeat() {
  stopHeartbeat()

  function tick() {
    if (!transport || !transport.isConnected()) return

    // 自适应心跳间隔
    const currentInterval = adaptiveHeartbeatInterval()

    // 心跳超时检查
    if (lastHeartbeatResponseTime > 0 && Date.now() - lastHeartbeatResponseTime > currentInterval + heartbeatTimeout) {
      console.warn('心跳超时，关闭连接')
      if (transport) transport.close()
      return
    }

    const now = Date.now()
    recordHeartbeatSent(now)
    sendWrapper({
      type: MSG_TYPE.HEARTBEAT,
      heartbeat: { timestamp: now }
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

  console.debug(`将在 ${currentReconnectDelay}ms 后尝试重连...`)

  reconnectTimer = setTimeout(() => {
    if (!stopReconnect) {
      connect(hostname, port, token, userId, handlers).catch(e => {
        console.warn('重连失败:', e)
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
    lastHeartbeatResponseTime = Date.now()

    // 带 success 字段的响应（ResponseMessage / AckMessage）
    const isSuccess = processedData.success
      || (processedData.response && processedData.response.success)
    if (isSuccess !== undefined) {
      if (!isSuccess) {
        const msg = (processedData.response ? processedData.response.message : processedData.message || '').toLowerCase()
        if (msg.includes('登录失败') || msg.includes('token无效') || msg.includes('token过期') || msg.includes('未授权') || msg.includes('unauthorized')) {
          console.debug('🔑 认证失败，停止重连')
          stopReconnect = true
          handlers.onAuthFailed(msg)
        }
        return
      }
    }

    // 心跳/ACK 响应
    if (processedData.type === MSG_TYPE.HEARTBEAT
        || (processedData.heartbeat && typeof processedData.heartbeat === 'object')) {
      lastHeartbeatResponseTime = Date.now()
      return
    }
    // ACK 确认：更新消息状态 + seqId 游标 + RTT 测量
    if (processedData.type === MSG_TYPE.ACK) {
      lastHeartbeatResponseTime = Date.now()
      const ack = processedData.ack || processedData

      // 心跳 ACK 回传 timestamp 用于精确 RTT 测量
      if (ack.timestamp > 0 && (!ack.messageId || ack.messageId === '')) {
        recordRttFromAck(ack.timestamp)
      }

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

    // sync_hint：服务端推送的增量同步提示，触发客户端拉取新消息
    // 格式：{ type: "sync_hint", ack: { clientId: conversationId, message: seqId } }
    if (processedData.type === MSG_TYPE.SYNC_HINT) {
      const hint = processedData.ack || processedData
      const conversationId = hint.clientId
      const seqId = parseInt(hint.message, 10)
      if (conversationId && seqId > 0 && storeRef) {
        storeRef.setConversationSeqId(conversationId, seqId)
        if (handlers.onmessage) handlers.onmessage(processedData)
      }
    }

    // 用户在线状态更新
    if (processedData.clientId !== undefined && processedData.online !== undefined) {
      // 传给上层
    }

    // 好友请求被接受通知
    if (processedData.type === MSG_TYPE.FRIEND_ACCEPTED && processedData.ack) {
      const accepterId = processedData.ack.clientId
      if (accepterId && handlers.onFriendAccepted) {
        handlers.onFriendAccepted(parseInt(accepterId))
      }
    }

    // 新消息通知
    if (processedData.type === MSG_TYPE.CHAT && processedData.chat) {
      showNotification(processedData.chat)
    } else if (processedData.type === MSG_TYPE.GROUP_CHAT && processedData.groupChat) {
      showNotification(processedData.groupChat)
    }

    // 同步响应
    if (processedData.type === MSG_TYPE.RESPONSE && processedData.response) {
      // 传给上层
    }
  }

  // 传给上层回调
  if (handlers.onmessage) {
    handlers.onmessage(processedData)
  }
}

// ── RTT 记录（基于服务端回传 timestamp） ───────────

let lastHbSendTime = 0
let lastHbTimestamp = 0   // 发送心跳时的客户端时间戳
let rttSamples = []
const MAX_RTT_SAMPLES = 10

/**
 * 记录心跳发送时间。
 * @param {number} timestamp - 发送时的 Date.now()
 */
function recordHeartbeatSent(timestamp) {
  lastHbSendTime = Date.now()
  lastHbTimestamp = timestamp
}

/**
 * 收到心跳 ACK 时调用，用服务端回传的 timestamp 计算精确 RTT。
 * @param {number} serverEchoTimestamp - 服务端回传的客户端时间戳
 */
function recordRttFromAck(serverEchoTimestamp) {
  if (lastHbTimestamp > 0 && serverEchoTimestamp === lastHbTimestamp) {
    const rtt = Date.now() - lastHbSendTime
    rttSamples.push(rtt)
    if (rttSamples.length > MAX_RTT_SAMPLES) rttSamples.shift()
    lastHbTimestamp = 0 // 防止重复计算
  }
}

/**
 * 获取平均 RTT（毫秒）。
 */
export function getAverageRtt() {
  if (rttSamples.length === 0) return 0
  return Math.round(rttSamples.reduce((a, b) => a + b, 0) / rttSamples.length)
}

/**
 * 获取 P90 RTT（毫秒）。
 */
export function getP90Rtt() {
  if (rttSamples.length === 0) return 0
  const sorted = [...rttSamples].sort((a, b) => a - b)
  return sorted[Math.floor(sorted.length * 0.9)]
}

/**
 * 自适应心跳间隔：基于 P90 RTT，范围 10-30 秒。
 * 目标：心跳间隔 = RTT * 3，留余量避免 bufferbloat 误触断连。
 */
function adaptiveHeartbeatInterval() {
  if (rttSamples.length < 3) return 30000 // 样本不足时用默认 30s
  const p90 = getP90Rtt()
  return Math.max(10000, Math.min(30000, p90 * 3))
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
  setStore,
  getOfflineQueueSize: async () => {
    const { getQueueSize } = await import('./offlineQueue')
    return getQueueSize()
  }
}

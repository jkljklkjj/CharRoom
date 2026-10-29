import { confirmMessageId as confirmStoredMessageId } from '../utils/messageDB'
import { discoverGatewayOwner } from './gatewayOwner'
import type { ChatTransport } from './transport/ChatTransport'
import type { MessageWrapper, ChatMessage, GroupChatMessage } from '../proto/messages'
import type { useStore } from '../store'
interface SocketHandlers {
  onAuthenticated?: () => void
  onopen?: () => void
  onmessage?: (message: MessageWrapper) => void | Promise<void>
  onclose?: (event?: unknown) => void
  onerror?: (error: unknown) => void
  onAuthFailed?: (reason: string, rejectedToken?: string) => void | boolean | Promise<boolean>
  onFriendAccepted?: (userId: number) => void
}
import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'
import i18n from '../i18n'
import { createTransport, buildWebTransportUrl, isWebTransportSupported } from './transport/TransportFactory'
import { saveToQueue, getPending, getDuePending, removeMessage, markAttempt, markFailed, retryMessage, MAX_RETRIES } from './offlineQueue'
import type { QueueEntry } from './offlineQueue'
import { getDeviceId, getDeviceType } from '../utils/device'
import { MSG_TYPE, DEVICE_TYPE_NUM, PROTO_DEVICE_TYPE } from '../proto/constants'
import { HandshakeBudget, handshakeRetryAfter } from './handshakeBudget'

// ── 内部变量 ────────────────────────────────────

let authenticated = false
let loginToken = ''
const handshakeBudgets = new Map<string, HandshakeBudget>()
let handshakeBudget: HandshakeBudget | null = null
let reconnectTarget: { hostname: string; port: number; userId: string } | null = null
let connectionGeneration = 0
let transport: ChatTransport | null = null          // ChatTransport 实例
let handlers: SocketHandlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimeoutTimer: ReturnType<typeof setTimeout> | null = null
let ackCheckTimer: ReturnType<typeof setTimeout> | null = null      // ACK 超时检查定时器
let cacheCleanupTimer: ReturnType<typeof setTimeout> | null = null  // 消息去重缓存清理定时器
let heartbeatInterval = 30000 // 30秒心跳
let heartbeatTimeout = 10000  // 心跳超时10秒
let currentReconnectDelay = 1000
let lastHeartbeatResponseTime = 0
let maxReconnectDelay = 30000
let stopReconnect = false
let currentUserId: string | null = null
const MAX_MESSAGE_CACHE = 1000
// 消息去重：Map<messageId, timestamp>，带 TTL 自动过期
const messageCache = new Map<string, number>()
const MESSAGE_TTL = 5 * 60 * 1000 // 5 分钟 TTL

// 乐观 UI：待确认消息跟踪 Map<messageId, PendingAckInfo>
interface PendingAckInfo {
  sendTime: number; warned: boolean; failed: boolean; retryCount: number
  accountId: string; wrapperObj: MessageWrapper; buffer: ArrayBuffer
  streamType: string; conversationId: string
}
const pendingAcks = new Map<string, PendingAckInfo>()
const ACK_TIMEOUT_MS = 16000  // 8-16s: 发送中 → 16s+: 失败
const MAX_ACK_RETRIES = 3     // ACK 超时后最多重发 3 次（服务端幂等去重，安全）

// ── Store 引用（依赖注入，替代 window.__chatStore） ──

let storeRef: ReturnType<typeof useStore> | null = null

/**
 * 注入 store 引用，chatSocket 内部需要读写 store 时使用。
 * 应在 App 初始化时调用一次。
 */
export function setStore(store: ReturnType<typeof useStore>) {
  storeRef = store
}

// ── 定时器生命周期管理 ──

function startTimers() {
  stopTimers()

  // 每秒检查待确认消息，超时标记为失败
  ackCheckTimer = setInterval(() => {
    if (!storeRef) return
    const now = Date.now()
    if (authenticated) void flushOfflineQueue()
    for (const [msgId, info] of pendingAcks) {
      if (now - info.sendTime >= ACK_TIMEOUT_MS && !info.failed) {
        info.failed = true
        void getPending(info.accountId, msgId).then(entry => {
          if (entry && info.accountId === currentUserId) return sendQueued(entry)
        }).catch(error => console.warn('Outbox retry failed:', error))
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
export async function connect(hostname: string, port: number, token: string, userId: string, { onopen, onmessage, onclose, onerror, onAuthFailed, onFriendAccepted, onAuthenticated }: SocketHandlers = {}) {
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

  authenticated = false
  const generation = ++connectionGeneration
  stopReconnect = false
  currentUserId = userId
  reconnectTarget = { hostname, port, userId }
  const budgetKey = `${userId}:${getDeviceId()}`
  handshakeBudget = handshakeBudgets.get(budgetKey) || new HandshakeBudget()
  handshakeBudgets.set(budgetKey, handshakeBudget)
  if (handshakeBudgets.size > 32) handshakeBudgets.delete(handshakeBudgets.keys().next().value!)
  handlers = {
    onopen: onopen || handlers.onopen,
    onmessage: onmessage || handlers.onmessage,
    onclose: onclose || handlers.onclose,
    onerror: onerror || handlers.onerror,
    onAuthFailed: onAuthFailed || (() => {}),
    onAuthenticated,
    onFriendAccepted: onFriendAccepted || (() => {})
  }

  // 断开旧的连接
  if (transport) {
    transport.close()
    transport = null
  }

  const wait = handshakeBudget.reserve()
  if (wait > 0) { scheduleReconnect(hostname, port, token, userId, wait); return null }

  // 创建 WebTransport 传输层
  const connection = createTransport()
  transport = connection

  // 设置事件回调
  transport.onopen = () => {
    if (generation !== connectionGeneration) return
    console.debug('✅ 连接成功')
    currentReconnectDelay = 1000

    // 启动定时器（连接生命周期内）
    startTimers()

    // 发送登录消息
    sendLogin(String(storeRef?.state.token || token))

    // Replay starts only after the server confirms this LOGIN.
    if (handlers.onopen) handlers.onopen()
  }

  transport.onmessage = (rawData) => {
    if (generation === connectionGeneration) void handleMessage(rawData, generation)
  }

  transport.onclose = (event) => {
    if (generation !== connectionGeneration) return
    console.warn('[CS] ❌ 连接关闭, stopReconnect=' + stopReconnect)
    console.warn('[CS] onclose 堆栈:', new Error().stack)
    stopHeartbeat()

    if (!stopReconnect) {
      scheduleReconnect(hostname, port, token, currentUserId || userId)
    }

    if (handlers.onclose) handlers.onclose(event)
  }

  transport.onerror = (event) => {
    if (generation !== connectionGeneration) return
    console.warn('[CS] 💥 传输错误:', event)
    console.warn('[CS] onerror 堆栈:', new Error().stack)
    if (handlers.onerror) handlers.onerror(event)
  }

  // 建立连接
  try {
    const endpoint = await discoverGatewayOwner(String(storeRef?.state.token || token), hostname, port)
    if (generation !== connectionGeneration) return null
    const finalUrl = buildWebTransportUrl(endpoint.host, endpoint.port)
    await connection.connect(finalUrl, token)
    return generation === connectionGeneration ? connection : null
  } catch (e) {
    if (generation === connectionGeneration && !stopReconnect) scheduleReconnect(hostname, port, token, userId)
    console.error('❌ 连接失败:', e)
    throw e
  }
}

async function sendLogin(token: string) {
  loginToken = token
  try {
    const result = await sendWrapper({
      type: MSG_TYPE.LOGIN,
      login: {
        token: token,
        deviceType: DEVICE_TYPE_NUM[getDeviceType()] ?? PROTO_DEVICE_TYPE.DEVICE_TYPE_UNSPECIFIED,
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
export async function sendWrapper(wrapperObj: MessageWrapper) {
  const generation = connectionGeneration, connection = transport
  const isChat = wrapperObj.type === MSG_TYPE.CHAT || wrapperObj.type === MSG_TYPE.GROUP_CHAT
  const accountId = String(storeRef?.state.accountId || currentUserId || '')
  const buffer = await encodeMessage(wrapperObj)
  if (generation !== connectionGeneration) return false
  if (!isChat) {
    if (!connection?.isConnected()) return false
    return connection.send(buffer, { streamType: getStreamType(wrapperObj), conversationId: resolveConversationId(wrapperObj) })
  }
  const opts = { streamType: getStreamType(wrapperObj), conversationId: resolveConversationId(wrapperObj) }
  await saveToQueue(accountId, wrapperObj, buffer, opts)
  if (generation !== connectionGeneration) return false
  const entry = await getPending(accountId, String(wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId))
  if (!entry) return false
  if (entry.state === 'failed') { await retryMessage(accountId, entry.messageId); entry.state = 'pending'; entry.retryCount = 0 }
  return sendQueued(entry)
}

/** Durable acceptance by the local outbox; transport success remains a separate result. */
export async function enqueueWrapper(wrapper: MessageWrapper, owner: string): Promise<void> {
  const generation = connectionGeneration
  if (!owner || owner !== String(storeRef?.state.accountId || currentUserId || '')) throw new Error('Stale sending account')
  const buffer = await encodeMessage(wrapper)
  if (owner !== String(storeRef?.state.accountId || currentUserId || '') || generation !== connectionGeneration) throw new Error('Sending session changed')
  if (buffer.byteLength > 65536) throw new Error('Message exceeds transport frame limit')
  const opts = { streamType: getStreamType(wrapper), conversationId: resolveConversationId(wrapper) }
  await saveToQueue(owner, wrapper, buffer, opts)
  // The durable write is the commit point. A later read/transport failure must not retain
  // the draft and encourage a new ID for an entry which is already queued.
  void getPending(owner, String(wrapper.chat?.messageId || wrapper.groupChat?.messageId))
    .then(entry => entry ? sendQueued(entry) : false)
    .catch(() => { /* Durable entry is retained for retry. */ })
}

const outgoing = new Map<string, Promise<boolean>>()
function sendQueued(entry: QueueEntry): Promise<boolean> {
  const key = `${connectionGeneration}:${entry.accountId}:${entry.messageId}`, active = outgoing.get(key)
  if (active) return active
  const generation = connectionGeneration, connection = transport
  const current = () => generation === connectionGeneration && authenticated && currentUserId === entry.accountId
  const sending = Promise.resolve().then(async () => {
    if (!current() || !connection?.isConnected()) return false
    if (entry.retryCount >= MAX_RETRIES) {
      await markFailed(entry.accountId, entry.messageId)
      if (current()) storeRef?.updateMessageStatus(entry.messageId, 'failed')
      return false
    }
    const updated = await markAttempt(entry.accountId, entry.messageId)
    if (!updated || !current()) return false
    const buffer = Uint8Array.from(entry.buffer).buffer
    const streamType = entry.opts.streamType || getStreamType(entry.wrapperObj)
    const conversationId = entry.opts.conversationId || resolveConversationId(entry.wrapperObj)
    // Register before write: a fast server ACK may arrive before write() resolves.
    pendingAcks.set(entry.messageId, { accountId: entry.accountId, sendTime: Date.now(), warned: false, failed: false,
      retryCount: updated.retryCount, wrapperObj: entry.wrapperObj, buffer, streamType, conversationId })
    storeRef?.updateMessageStatus(entry.messageId, 'sending')
    try { return await connection.send(buffer, { streamType, conversationId }) }
    catch { return false }
    // A successful network write never removes the durable entry. Only its server ACK does.
  }).finally(() => { if (outgoing.get(key) === sending) outgoing.delete(key) })
  outgoing.set(key, sending)
  return sending
}

/**
 * 根据消息类型确定流类型。
 */
function getStreamType(wrapperObj: MessageWrapper): 'control' | 'chat' {
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
function resolveConversationId(wrapperObj: MessageWrapper) {
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
let flushing: { generation: number; promise: Promise<void> } | null = null
async function flushOfflineQueue() {
  if (flushing?.generation === connectionGeneration || !authenticated || !currentUserId) return
  const accountId = currentUserId, generation = connectionGeneration
  const flight = (async () => {
    for (const entry of await getDuePending(accountId)) {
      if (generation !== connectionGeneration || !authenticated) return
      if (entry.state === 'failed') continue
      const pending = pendingAcks.get(entry.messageId)
      if (pending && Date.now() - pending.sendTime < ACK_TIMEOUT_MS) continue
      await sendQueued(entry)
    }
  })().catch(error => console.warn('Outbox replay failed:', error)).finally(() => { if (flushing?.promise === flight) flushing = null })
  flushing = { generation, promise: flight }
  await flight
}

/**
 * 发送 ACK 确认消息。
 */
export async function sendAck(messageId: string) {
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
  authenticated = false
  flushing = null
  connectionGeneration++
  messageCache.clear()
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

    // 心跳超时检查：仅告警，不关闭连接（子流替代后 ACK 走子流，控制流收不到是正常的）
    if (lastHeartbeatResponseTime > 0 && Date.now() - lastHeartbeatResponseTime > currentInterval + heartbeatTimeout) {
      console.debug('[HB] 控制流无响应（ACK 走子流，正常）')
    }

    const now = Date.now()
    recordHeartbeatSent(now)
    sendWrapper({
      type: MSG_TYPE.HEARTBEAT,
      heartbeat: { timestamp: now }
    }).catch(e => {
      console.warn('[HB] 心跳发送失败:', e.message)
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

function scheduleReconnect(hostname: string, port: number, token: string, userId: string, minimumDelay = 0) {
  if (reconnectTimer) clearTimeout(reconnectTimer)

  const delayMs = Math.max(currentReconnectDelay, minimumDelay)
  console.debug(`将在 ${delayMs}ms 后尝试重连...`)

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
  }, delayMs)
}

// ── 消息处理 ────────────────────────────────────

/**
 * 处理收到的消息（二进制数据）。
 */
async function handleMessage(rawData: ArrayBuffer | Blob | MessageWrapper, generation = connectionGeneration) {
  let processedData: MessageWrapper
  if (rawData instanceof ArrayBuffer || rawData instanceof Blob) {
    try { processedData = await decodeMessage(rawData instanceof Blob ? await rawData.arrayBuffer() : rawData) }
    catch (error) { console.warn('Protobuf 解码失败:', error); return }
  } else { processedData = rawData }

  if (generation !== connectionGeneration) return

  // 处理服务端响应
  if (processedData && typeof processedData === 'object') {
    lastHeartbeatResponseTime = Date.now()

    if (processedData.type === MSG_TYPE.RESPONSE && processedData.response?.success === false) {
      const retry = handshakeRetryAfter(processedData.response.message || '')
      if (retry != null) {
        authenticated = false
        stopHeartbeat()
        handshakeBudget?.block(retry)
        // Close invokes the ordinary reconnect callback; override its timer with the server cooldown.
        transport?.close()
        if (reconnectTarget && !stopReconnect) scheduleReconnect(reconnectTarget.hostname, reconnectTarget.port,
          String(storeRef?.state.token || ''), reconnectTarget.userId, retry)
        return
      }
    }

    if (processedData.type === MSG_TYPE.RESPONSE && processedData.response &&
      ['OWNER_REDIRECT', 'OWNER_UNAVAILABLE', 'SERVER_BUSY'].includes(processedData.response.message || '')) {
    transport?.close()
    return
  }

    // 带 success 字段的响应（ResponseMessage / AckMessage）
    const isSuccess = processedData.response?.success
    if (isSuccess !== undefined) {
      if (!isSuccess) {
        const msg = (processedData.response?.message || '').toLowerCase()
        if (msg.includes('登录失败') || msg.includes('token无效') || msg.includes('token过期') || msg.includes('未授权') || msg.includes('unauthorized')) {
          console.debug('🔑 认证失败，停止重连')
          stopReconnect = true
          authenticated = false
          stopHeartbeat()
          transport?.close()
          const recover = await handlers.onAuthFailed?.(msg, loginToken)
          if (generation === connectionGeneration && recover && reconnectTarget) {
            stopReconnect = false
            scheduleReconnect(reconnectTarget.hostname, reconnectTarget.port, String(storeRef?.state.token || ''), reconnectTarget.userId)
          }
        }
        return
      }
    }

  if (processedData.type === MSG_TYPE.RESPONSE && processedData.response?.success &&
        String(processedData.response.clientId) === currentUserId && !authenticated) {
      authenticated = true
      handlers.onAuthenticated?.()
      startHeartbeat()
      void flushOfflineQueue()
      return
    }
    // 心跳/ACK 响应
    if (processedData.type === MSG_TYPE.HEARTBEAT
        || (processedData.heartbeat && typeof processedData.heartbeat === 'object')) {
      lastHeartbeatResponseTime = Date.now()
      return
    }
    // ACK confirms this send; history synchronization owns the applied cursor.
    if (processedData.type === MSG_TYPE.ACK) {
      lastHeartbeatResponseTime = Date.now()
      const ack = processedData.ack
      if (!ack) return

      // 心跳 ACK 回传 timestamp 用于精确 RTT 测量
      if (Number(ack.timestamp) > 0 && (!ack.messageId || ack.messageId === '')) {
        recordRttFromAck(Number(ack.timestamp))
      }

      const ackedMsgId = ack.messageId
      const pending = ackedMsgId ? pendingAcks.get(ackedMsgId) : null
      if (ackedMsgId && pending && pending.accountId === currentUserId && ack.conversationId && ack.seqId != null) {
        if (ack.success !== true) await markFailed(pending.accountId, ackedMsgId)
        else {
          if (ack.serverMessageId) {
            const localConversation = ack.conversationId.startsWith('group:') ? ack.conversationId
              : ack.conversationId.replace(/^user:/, '').split(':').find(id => id !== pending.accountId) || pending.accountId
            await confirmStoredMessageId(pending.accountId, localConversation, ackedMsgId, ack.serverMessageId, String(ack.seqId))
          }
          await removeMessage(pending.accountId, ackedMsgId)
        }
        if (generation !== connectionGeneration) return
        pendingAcks.delete(ackedMsgId)
        if (ack.success === true && ack.serverMessageId) storeRef?.confirmMessageId(ackedMsgId, ack.serverMessageId, String(ack.seqId))
        else storeRef?.updateMessageStatus(ackedMsgId, ack.success !== true ? 'failed' : 'sent')
      }
      if (ack.success === true && ack.seqId != null && ack.conversationId) {
        handlers.onmessage?.({ type: MSG_TYPE.SYNC_HINT,
          ack: { clientId: ack.conversationId, message: String(ack.seqId) } })
      }
      return
    }

    // sync_hint：服务端推送的增量同步提示，触发客户端拉取新消息
    // 格式：{ type: "sync_hint", ack: { clientId: conversationId, message: seqId } }
    if (processedData.type === MSG_TYPE.SYNC_HINT) {
      const hint = processedData.ack
      if (!hint) return
      const conversationId = hint.clientId
      const seqId = hint.message || ''
      if (conversationId && /^\d+$/.test(seqId) && BigInt(seqId) > 0n) handlers.onmessage?.(processedData)
      return
    }


    // 好友请求被接受通知
    if (processedData.type === MSG_TYPE.FRIEND_ACCEPTED && processedData.ack) {
      const accepterId = processedData.ack.clientId
      if (accepterId && handlers.onFriendAccepted) {
        handlers.onFriendAccepted(parseInt(accepterId))
      }
    }

    if ((processedData.type === MSG_TYPE.CHAT && processedData.chat) ||
        (processedData.type === MSG_TYPE.GROUP_CHAT && processedData.groupChat)) {
      if (!handlers.onmessage) return
      try {
        await handlers.onmessage(processedData)
        if (generation !== connectionGeneration) return
        const message = processedData.chat || processedData.groupChat!
        showNotification(message)
        if (message.messageId) await sendAck(message.messageId)
      } catch (error) { console.warn('Incoming message was not committed:', error) }
      return
    }

    // 同步响应
    if (processedData.type === MSG_TYPE.RESPONSE && processedData.response) {
      // 传给上层
    }
  }

  // 传给上层回调
  if (handlers.onmessage) {
    try { await handlers.onmessage(processedData) }
    catch (error) { console.warn('Message callback failed:',error) }
  }
}

// ── RTT 记录（基于服务端回传 timestamp） ───────────

let lastHbSendTime = 0
let lastHbTimestamp = 0   // 发送心跳时的客户端时间戳
let rttSamples: number[] = []
const MAX_RTT_SAMPLES = 10

/**
 * 记录心跳发送时间。
 * @param {number} timestamp - 发送时的 Date.now()
 */
function recordHeartbeatSent(timestamp: number) {
  lastHbSendTime = Date.now()
  lastHbTimestamp = timestamp
}

/**
 * 收到心跳 ACK 时调用，用服务端回传的 timestamp 计算精确 RTT。
 * @param {number} serverEchoTimestamp - 服务端回传的客户端时间戳
 */
function recordRttFromAck(serverEchoTimestamp: number) {
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

function showNotification(msg: ChatMessage | GroupChatMessage) {
  if (document.visibilityState === 'visible') return
  if (!('Notification' in window) || Notification.permission !== 'granted') return

  // 优先显示发送者昵称（好友列表查找），找不到时回退账号 ID
  const senderId = String(msg.userId || '')
  const sender = senderId && storeRef
    ? storeRef.state.users.find(u => String(u.id) === senderId)
    : undefined
  const title = sender?.name || sender?.username || senderId || '新消息'
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
  enqueueWrapper,
  sendAck,
  readyState,
  getAverageRtt,
  setStore,
  getOfflineQueueSize: async () => {
    const { getQueueSize } = await import('./offlineQueue')
    return getQueueSize(String(currentUserId || storeRef?.state.accountId || ''))
  }
}

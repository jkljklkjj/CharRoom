import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'

let socket = null
let pendingQueue = []
let handlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }
let reconnectTimer = null
let heartbeatTimer = null
let heartbeatInterval = 30000 // 30秒心跳
let heartbeatTimeout = 5000 // 心跳超时5秒
let currentReconnectDelay = 1000 // 初始重连间隔1秒
let maxReconnectDelay = 30000 // 最大重连间隔30秒
let isReconnecting = false
let stopReconnect = false
const MAX_QUEUE_SIZE = 1000 // 消息队列最大长度
const MAX_MESSAGE_CACHE = 1000 // 消息去重缓存最大长度
const receivedMessageIds = new Set() // 消息去重缓存

// Note: browsers cannot set custom headers on WebSocket handshake. We pass token as query `?token=`.
export function connect(wsUrl, token, { onopen, onmessage, onclose, onerror } = {}) {
  if (socket && socket.readyState === WebSocket.OPEN) return socket

  stopReconnect = false
  handlers = {
    onopen: onopen || handlers.onopen,
    onmessage: onmessage || handlers.onmessage,
    onclose: onclose || handlers.onclose,
    onerror: onerror || handlers.onerror
  }

  // 对token进行简单编码，提高安全性
  const encodedToken = token ? btoa(encodeURIComponent(token)) : ''
  const url = encodedToken ? `${wsUrl}?token=${encodedToken}` : wsUrl

  socket = new WebSocket(url)
  socket.binaryType = 'arraybuffer'

  socket.addEventListener('open', (e) => {
    console.log('WebSocket连接成功')
    isReconnecting = false
    currentReconnectDelay = 1000

    handlers.onopen(e)
    // flush queue (buffers)
    flushQueue()
    // 启动心跳
    startHeartbeat()
  })

  socket.addEventListener('message', async (e) => {
    const data = e.data
    let processedData = data

    // 处理二进制消息
    if (data instanceof ArrayBuffer || data instanceof Blob) {
      let buf
      if (data instanceof Blob) buf = await data.arrayBuffer()
      else buf = data
      try {
        processedData = await decodeMessage(buf)
      } catch (err) {
        // could not parse protobuf, pass raw
        processedData = buf
      }
    } else {
      // 处理文本消息
      try {
        processedData = JSON.parse(data)
      } catch (_) {}
    }

    // 消息去重
    if (processedData && typeof processedData === 'object') {
      const messageId = processedData.messageId ||
                       (processedData.payload && processedData.payload.timestamp) ||
                       (processedData.chat && processedData.chat.timestamp)

      if (messageId) {
        if (receivedMessageIds.has(messageId)) {
          console.log('收到重复消息，忽略:', messageId)
          return
        }
        // 添加到去重缓存
        receivedMessageIds.add(messageId)
        // 缓存超过大小，移除最旧的
        if (receivedMessageIds.size > MAX_MESSAGE_CACHE) {
          const firstKey = receivedMessageIds.values().next().value
          receivedMessageIds.delete(firstKey)
        }
      }
    }

    handlers.onmessage(processedData)
  })

  socket.addEventListener('close', (e) => {
    console.log('WebSocket连接关闭:', e.code, e.reason)
    stopHeartbeat()

    if (!stopReconnect && !isReconnecting) {
      // 自动重连
      scheduleReconnect(wsUrl, token)
    }

    handlers.onclose(e)
  })

  socket.addEventListener('error', (e) => {
    console.error('WebSocket错误:', e)
    handlers.onerror(e)
  })

  return socket
}

/**
 * 启动心跳
 */
function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      // 发送心跳消息
      sendWrapper({
        type: 'heartbeat',
        timestamp: Date.now()
      }).catch(() => {
        // 心跳发送失败，关闭连接触发重连
        socket.close()
      })
    }
  }, heartbeatInterval)
}

/**
 * 停止心跳
 */
function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/**
 * 调度重连
 */
function scheduleReconnect(wsUrl, token) {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }

  isReconnecting = true
  console.log(`将在 ${currentReconnectDelay}ms 后尝试重连...`)

  reconnectTimer = setTimeout(() => {
    if (!stopReconnect) {
      connect(wsUrl, token, handlers)
      // 指数退避
      currentReconnectDelay = Math.min(currentReconnectDelay * 2, maxReconnectDelay)
    }
  }, currentReconnectDelay)
}

/**
 * 刷新消息队列，发送所有缓存的消息
 */
function flushQueue() {
  if (pendingQueue.length === 0) return

  console.log('发送队列中的消息，共', pendingQueue.length, '条')
  const queue = [...pendingQueue]
  pendingQueue = []

  queue.forEach(buffer => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      try {
        socket.send(buffer)
      } catch (e) {
        console.error('发送缓存消息失败:', e)
        // 发送失败重新入队
        if (pendingQueue.length < MAX_QUEUE_SIZE) {
          pendingQueue.push(buffer)
        }
      }
    }
  })
}

export async function sendWrapper(wrapperObj) {
  try {
    const buffer = await encodeMessage(wrapperObj)
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(buffer)
      return true
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
    console.error('encode error', e)
    return false
  }
}

/**
 * XSS防护，净化消息内容
 */
export function sanitizeMessage(content) {
  return DOMPurify.sanitize(content, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'a', 'code', 'pre', 'br'],
    ALLOWED_ATTR: ['href', 'title', 'target', 'class'],
    ALLOW_UNKNOWN_PROTOCOLS: false,
    FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover']
  })
}

export function close() {
  stopReconnect = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  stopHeartbeat()

  if (socket) {
    socket.close()
    socket = null
  }

  // 清空队列
  pendingQueue = []
  receivedMessageIds.clear()
}

export function readyState() {
  return socket ? socket.readyState : WebSocket.CLOSED
}

export function isConnected() {
  return socket && socket.readyState === WebSocket.OPEN
}

export default { connect, sendWrapper, close, readyState, isConnected, sanitizeMessage }

import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'

let socket = null
let pendingQueue = []
let handlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }
let reconnectTimer = null
let heartbeatTimer = null
let heartbeatTimeoutTimer = null
let heartbeatInterval = 30000 // 30秒心跳
let heartbeatTimeout = 5000 // 心跳超时5秒
let currentReconnectDelay = 1000 // 初始重连间隔1秒
let maxReconnectDelay = 30000 // 最大重连间隔30秒
let isReconnecting = false
let stopReconnect = false
let currentUserId = null // 当前登录用户ID
const MAX_QUEUE_SIZE = 1000 // 消息队列最大长度
const MAX_MESSAGE_CACHE = 1000 // 消息去重缓存最大长度
const receivedMessageIds = new Set() // 消息去重缓存
const messageIdQueue = [] // 消息ID队列，维护插入顺序用于LRU淘汰

// Note: browsers cannot set custom headers on WebSocket handshake. We pass token as query `?token=`.
export function connect(wsUrl, token, userId, { onopen, onmessage, onclose, onerror } = {}) {
  console.log('🔌 尝试建立WebSocket连接:', { wsUrl, hasToken: !!token, userId, existingSocket: !!socket, readyState: socket?.readyState })

  if (socket && socket.readyState === WebSocket.OPEN) {
    console.log('✅ 连接已存在，复用现有连接')
    return socket
  }

  stopReconnect = false
  currentUserId = userId // 保存当前用户ID
  handlers = {
    onopen: onopen || handlers.onopen,
    onmessage: onmessage || handlers.onmessage,
    onclose: onclose || handlers.onclose,
    onerror: onerror || handlers.onerror
  }

  // 构造纯净的WebSocket URL，不带任何参数
  let url = wsUrl
  console.log('🔗 最终WebSocket URL:', url)

  try {
    socket = new WebSocket(url)
    socket.binaryType = 'arraybuffer'
    console.log('🆕 新WebSocket实例已创建，将通过应用层Login消息认证')
  } catch (err) {
    console.error('❌ 创建WebSocket实例失败:', err)
    throw err
  }

  socket.addEventListener('open', (e) => {
    console.log('✅ WebSocket连接成功，readyState:', socket.readyState)
    isReconnecting = false
    currentReconnectDelay = 1000

    // 发送登录消息完成应用层认证（确保在next tick发送，避免状态未就绪）
    setTimeout(async () => {
      if (token && socket.readyState === WebSocket.OPEN) {
        try {
          console.log('📤 正在发送登录消息，token长度:', token.length)
          const result = await sendWrapper({
            type: 'login',
            login: { targetClientId: token }
          })
          console.log('✅ 登录消息发送结果:', result)
        } catch (err) {
          console.error('❌ 发送登录消息失败:', err)
        }
      }
    }, 0)

    handlers.onopen(e)
    // flush queue (buffers) - 先不flush，等登录完成后再发
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

    // 处理登录响应
    if (processedData && typeof processedData === 'object' && processedData.success !== undefined) {
      console.log('🔐 收到登录响应:', processedData)
      if (processedData.success) {
        console.log('✅ 登录成功，开始发送队列消息')
        // 登录成功后flush消息队列
        flushQueue()
      } else {
        console.error('❌ 登录失败:', processedData.message)
      }
    }

    // 消息去重和ACK确认
    if (processedData && typeof processedData === 'object') {
      let messageId = null
      let isChatOrGroupMessage = false

      // 从不同消息类型中提取messageId
      if (processedData.chat && processedData.chat.messageId) {
        messageId = processedData.chat.messageId
        isChatOrGroupMessage = true
      } else if (processedData.groupChat && processedData.groupChat.messageId) {
        messageId = processedData.groupChat.messageId
        isChatOrGroupMessage = true
      }

      if (messageId) {
        if (receivedMessageIds.has(messageId)) {
          console.log('收到重复消息，忽略:', messageId)
          return
        }
        // 添加到去重缓存
        receivedMessageIds.add(messageId)
        messageIdQueue.push(messageId)
        // 缓存超过大小，移除最旧的
        if (receivedMessageIds.size > MAX_MESSAGE_CACHE) {
          const oldestId = messageIdQueue.shift()
          receivedMessageIds.delete(oldestId)
        }

        // 发送ACK确认
        if (isChatOrGroupMessage) {
          sendAck(messageId).catch(err => {
            console.warn('发送ACK失败:', err)
          })
        }
      }
    }

    handlers.onmessage(processedData)
  })

  socket.addEventListener('close', (e) => {
    console.log('❌ WebSocket连接关闭: code=', e.code, 'reason=', e.reason, 'wasClean=', e.wasClean)
    stopHeartbeat()

    if (!stopReconnect && !isReconnecting) {
      // 自动重连
      scheduleReconnect(wsUrl, token, currentUserId)
    }

    handlers.onclose(e)
  })

  socket.addEventListener('error', (e) => {
    console.error('💥 WebSocket错误:', e)
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
      // 启动心跳超时定时器
      heartbeatTimeoutTimer = setTimeout(() => {
        console.log('心跳超时，关闭连接触发重连')
        if (socket) {
          socket.close()
        }
      }, heartbeatTimeout)

      // 发送心跳消息
      sendWrapper({
        type: 'heartbeat',
        timestamp: Date.now()
      }).then(() => {
        // 心跳发送成功，清除超时
        clearTimeout(heartbeatTimeoutTimer)
      }).catch(() => {
        // 心跳发送失败，清除超时并关闭连接
        clearTimeout(heartbeatTimeoutTimer)
        if (socket) {
          socket.close()
        }
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
  if (heartbeatTimeoutTimer) {
    clearTimeout(heartbeatTimeoutTimer)
    heartbeatTimeoutTimer = null
  }
}

/**
 * 调度重连
 */
function scheduleReconnect(wsUrl, token, userId) {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }

  isReconnecting = true
  console.log(`将在 ${currentReconnectDelay}ms 后尝试重连...`)

  reconnectTimer = setTimeout(() => {
    if (!stopReconnect) {
      connect(wsUrl, token, userId, handlers)
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
 * 发送ACK确认消息
 * @param {string} messageId 要确认的消息ID
 */
export async function sendAck(messageId) {
  if (!currentUserId || !messageId) {
    return false
  }

  return sendWrapper({
    type: 'ack',
    ack: {
      messageId: messageId
    }
  })
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

  // 清空状态
  pendingQueue = []
  receivedMessageIds.clear()
  messageIdQueue.length = 0
  currentUserId = null
}

export function readyState() {
  return socket ? socket.readyState : WebSocket.CLOSED
}

export function isConnected() {
  return socket && socket.readyState === WebSocket.OPEN
}

export default { connect, sendWrapper, sendAck, close, readyState, isConnected, sanitizeMessage }

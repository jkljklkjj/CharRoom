import { encodeMessage, decodeMessage } from '../proto'
import DOMPurify from 'dompurify'

let socket = null
let pendingQueue = []
let handlers = { onopen: () => {}, onmessage: () => {}, onclose: () => {}, onerror: () => {} }
let reconnectTimer = null
let heartbeatTimer = null
let heartbeatTimeoutTimer = null
let heartbeatInterval = 30000 // 30秒心跳
let heartbeatTimeout = 10000 // 心跳超时10秒（给服务端更多响应时间）
let currentReconnectDelay = 1000 // 初始重连间隔1秒
let lastHeartbeatResponseTime = 0 // 最后一次收到心跳响应的时间
let maxReconnectDelay = 30000 // 最大重连间隔30秒
let isReconnecting = false
let stopReconnect = false
let currentUserId = null // 当前登录用户ID
const MAX_QUEUE_SIZE = 1000 // 消息队列最大长度
const MAX_MESSAGE_CACHE = 1000 // 消息去重缓存最大长度
const receivedMessageIds = new Set() // 消息去重缓存
const messageIdQueue = [] // 消息ID队列，维护插入顺序用于LRU淘汰

// Note: browsers cannot set custom headers on WebSocket handshake. We pass token as query `?token=`.
export function connect(wsUrl, token, userId, { onopen, onmessage, onclose, onerror, onAuthFailed } = {}) {
  console.log('🔌 尝试建立WebSocket连接:', { wsUrl, hasToken: !!token, userId, existingSocket: !!socket, readyState: socket?.readyState })

  // token为空时不允许连接，防止无限重连
  if (!token || typeof token !== 'string' || token.trim() === '') {
    console.error('❌ 连接失败：token为空')
    stopReconnect = true
    if (onAuthFailed) {
      onAuthFailed('token不能为空')
    }
    throw new Error('WebSocket连接失败：认证凭证为空')
  }

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
    onerror: onerror || handlers.onerror,
    onAuthFailed: onAuthFailed || (() => {})
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
            login: { token: token }
          })
          console.log('✅ 登录消息发送结果:', result)
        } catch (err) {
          console.error('❌ 发送登录消息失败:', err)
        }
      }
    }, 0)

    handlers.onopen(e)
    // flush queue (buffers) - 先不flush，等登录完成后再发
    // 启动心跳保活
    startHeartbeat()
  })

  socket.addEventListener('message', async (e) => {
    console.log('📥 收到WebSocket原始消息:', e.data, '类型:', typeof e.data, '大小:', e.data?.byteLength || e.data?.length)
    const data = e.data
    let processedData = data

    // 处理二进制消息
    if (data instanceof ArrayBuffer || data instanceof Blob) {
      let buf
      if (data instanceof Blob) buf = await data.arrayBuffer()
      else buf = data
      console.log('🔍 二进制消息长度:', buf.byteLength)
      try {
        processedData = await decodeMessage(buf)
        console.log('✅ Protobuf解码成功:', processedData)
      } catch (err) {
        console.error('❌ Protobuf解码失败:', err, '原始数据:', new Uint8Array(buf))
        // could not parse protobuf, pass raw
        processedData = buf
      }
    } else {
      // 处理文本消息
      console.log('📄 收到文本消息:', data)
      try {
        processedData = JSON.parse(data)
      } catch (_) {}
    }

    // 处理登录/心跳等服务器响应
    if (processedData && typeof processedData === 'object') {
      // 处理带success字段的响应消息
      if (processedData.success !== undefined) {
        console.log('🔐 收到响应消息:', processedData)
        if (processedData.success) {
          if (processedData.message === 'heartbeat') {
            // 心跳只是保活，不需要传给页面层
            console.log('❤️ 收到 heartbeat 响应')
            lastHeartbeatResponseTime = Date.now()
            // 心跳响应成功，清除超时定时器
            if (heartbeatTimeoutTimer) {
              clearTimeout(heartbeatTimeoutTimer)
              heartbeatTimeoutTimer = null
            }
            return // 心跳响应不需要继续处理
          } else {
            console.log('✅ 登录响应成功，开始发送队列消息')
            // 登录成功，更新心跳响应时间
            lastHeartbeatResponseTime = Date.now()
            // 登录成功后flush消息队列
            flushQueue()
          }
        } else {
          console.error('❌ 响应失败:', processedData.message)
          const msg = (processedData.message || '').toLowerCase()
          // 认证相关错误，通知上层
          if (msg.includes('登录失败') || msg.includes('token无效') || msg.includes('token过期') || msg.includes('未授权') || msg.includes('unauthorized')) {
            console.log('🔑 认证失败，停止重连并通知上层')
            stopReconnect = true // 停止自动重连
            handlers.onAuthFailed(processedData.message)
          }
          return // 响应消息处理完成
        }
      }

      // 处理服务端主动发送的心跳消息（不带success字段）
      if (processedData.type === 'heartbeat' || (processedData.heartbeat && typeof processedData.heartbeat === 'object')) {
        console.log('❤️ 收到服务端主动心跳，发送响应')
        // 回复心跳响应
        sendWrapper({
          type: 'heartbeat',
          heartbeat: {
            timestamp: Date.now()
          }
        }).catch(err => {
          console.warn('发送心跳响应失败:', err)
        })
        // 更新心跳响应时间
        lastHeartbeatResponseTime = Date.now()
        return // 心跳消息不需要继续处理
      }

      // 处理用户在线状态更新（CHECK响应）
      if (processedData.clientId !== undefined && processedData.online !== undefined) {
        const clientId = parseInt(processedData.clientId)
        const online = processedData.online
        console.log(`👤 收到用户在线状态更新: userId=${clientId}, online=${online}`)
        // 这里不需要额外处理，直接传给上层Vuex/store更新状态即可
        // 上层会负责更新用户列表中的在线状态
      }

      // 新消息系统通知
      if (processedData.type === 'chat' && processedData.chat) {
        showNotification(processedData.chat)
      } else if (processedData.type === 'groupChat' && processedData.groupChat) {
        showNotification(processedData.groupChat, true)
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
      } else if (processedData.agentChat && processedData.agentChat.messageId) {
        messageId = processedData.agentChat.messageId
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
  lastHeartbeatResponseTime = Date.now() // 初始化响应时间
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      // 检查上次心跳响应是否超时
      const timeSinceLastResponse = Date.now() - lastHeartbeatResponseTime
      if (timeSinceLastResponse > heartbeatInterval + heartbeatTimeout) {
        console.log(`心跳超时，距离上次响应已过去 ${timeSinceLastResponse}ms，关闭连接触发重连`)
        if (socket) {
          socket.close()
        }
        return
      }

      // 发送心跳消息（不需要等待发送结果，超时由上面的全局检查处理）
      sendWrapper({
        type: 'heartbeat',
        heartbeat: {
          timestamp: Date.now()
        }
      }).catch(() => {
        // 心跳发送失败，直接关闭连接
        console.log('心跳发送失败，关闭连接')
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

/**
 * 新消息系统通知
 * @param {Object} message 消息对象
 * @param {Boolean} isGroup 是否为群聊消息
 */
function showNotification(message, isGroup = false) {
  // 页面可见时不弹通知
  if (document.visibilityState === 'visible') return

  // 没有通知权限，先申请
  if (!('Notification' in window)) return
  if (Notification.permission !== 'granted') {
    Notification.requestPermission()
    return
  }

  const userId = currentUserId ? String(currentUserId) : ''
  const senderId = String(message.userId || '')
  // 自己发的消息不弹通知
  if (senderId === userId) return

  let title = isGroup ? '群聊消息' : '新消息'
  let body = message.content || '收到一条新消息'

  // 显示通知
  const notification = new Notification(title, {
    body: body,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-192x192.png',
    tag: isGroup ? `group-${message.targetClientId}` : `user-${senderId}`, // 同一会话的消息合并
    renotify: true,
    silent: false
  })

  // 点击通知跳转到对应聊天
  notification.onclick = () => {
    window.focus()
    notification.close()
    // 可以在这里添加跳转到对应聊天的逻辑
  }
}

// 页面加载时申请通知权限
if ('Notification' in window && Notification.permission === 'default') {
  // 延迟到用户交互后再申请，避免被浏览器拦截
  document.addEventListener('click', function requestNotificationPermission() {
    Notification.requestPermission()
    document.removeEventListener('click', requestNotificationPermission)
  }, { once: true })
}

export default { connect, sendWrapper, sendAck, close, readyState, isConnected, sanitizeMessage }

/**
 * PWA 离线消息队列（IndexedDB 持久化）。
 *
 * 当用户发送消息时网络不可用，消息存入 IndexedDB。
 * 网络恢复后自动重发并清理已发送的消息。
 */

const DB_NAME = 'chatlite_offline'
const DB_VERSION = 1
const STORE_NAME = 'pending_messages'

let db = null

/**
 * 打开 IndexedDB 连接（懒初始化）。
 */
function openDB() {
  if (db) return Promise.resolve(db)
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = (event) => {
      const database = event.target.result
      if (!database.objectStoreNames.contains(STORE_NAME)) {
        const store = database.createObjectStore(STORE_NAME, { keyPath: 'messageId' })
        store.createIndex('timestamp', 'timestamp', { unique: false })
      }
    }
    request.onsuccess = (event) => {
      db = event.target.result
      resolve(db)
    }
    request.onerror = (event) => {
      console.error('[OfflineQueue] IndexedDB 打开失败:', event.target.error)
      reject(event.target.error)
    }
  })
}

/**
 * 保存消息到离线队列。
 * @param {object} wrapperObj - protobuf wrapper 对象
 * @param {ArrayBuffer} buffer - 编码后的 protobuf 二进制
 * @param {object} opts - { streamType, conversationId }
 */
export async function saveToQueue(wrapperObj, buffer, opts) {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    const entry = {
      messageId: wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId || `offline_${Date.now()}`,
      wrapperObj,
      buffer: Array.from(new Uint8Array(buffer)), // IndexedDB 不支持 ArrayBuffer，转为数组
      opts,
      timestamp: Date.now(),
      retryCount: 0
    }

    store.put(entry)

    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve(true)
      tx.onerror = (e) => {
        console.warn('[OfflineQueue] 保存失败:', e.target.error)
        reject(e.target.error)
      }
    })
  } catch (e) {
    console.warn('[OfflineQueue] saveToQueue 异常:', e)
    return false
  }
}

/**
 * 获取所有待发送的消息。
 * @returns {Array} 待发送消息列表
 */
export async function getAllPending() {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const request = store.getAll()

    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result || [])
      request.onerror = (e) => reject(e.target.error)
    })
  } catch (e) {
    console.warn('[OfflineQueue] getAllPending 异常:', e)
    return []
  }
}

/**
 * 删除已发送的消息。
 * @param {string} messageId
 */
export async function removeMessage(messageId) {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    store.delete(messageId)
  } catch (e) {
    console.warn('[OfflineQueue] removeMessage 异常:', e)
  }
}

/**
 * 清空整个离线队列。
 */
export async function clearQueue() {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    store.clear()
  } catch (e) {
    console.warn('[OfflineQueue] clearQueue 异常:', e)
  }
}

/**
 * 获取队列中的消息数量。
 */
export async function getQueueSize() {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const request = store.count()

    return new Promise((resolve) => {
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => resolve(0)
    })
  } catch (e) {
    return 0
  }
}

/**
 * 重试发送单条消息（更新重试计数）。
 * @param {string} messageId
 */
export async function incrementRetry(messageId) {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const request = store.get(messageId)

    request.onsuccess = () => {
      const entry = request.result
      if (entry) {
        entry.retryCount = (entry.retryCount || 0) + 1
        store.put(entry)
      }
    }
  } catch (e) {
    // ignore
  }
}

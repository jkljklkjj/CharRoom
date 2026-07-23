/**
 * 消息 IndexedDB 存储
 * 替代 localStorage 的 O(n) 读写，提供 O(1) 的消息追加和批量读取
 */

const DB_NAME = 'charroom_messages'
const DB_VERSION = 1
const STORE_NAME = 'messages'

/**
 * 打开 IndexedDB 连接
 */
function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)

    request.onupgradeneeded = (event) => {
      const db = event.target.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' })
        // 创建索引用于按会话查询
        store.createIndex('accountId', 'accountId', { unique: false })
        store.createIndex('conversationId', 'conversationId', { unique: false })
        store.createIndex('accountId_conversationId', ['accountId', 'conversationId'], { unique: false })
      }
    }
  })
}

/**
 * 生成消息存储 ID
 */
function messageId(accountId, conversationId, messageIndex) {
  return `${accountId}:${conversationId}:${messageIndex}`
}

/**
 * 保存单条消息
 */
export async function saveMessage(accountId, conversationId, message, index) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const id = messageId(accountId, conversationId, index)
    store.put({
      id,
      accountId,
      conversationId,
      message,
      index,
      timestamp: message.time || message.timestamp || Date.now()
    })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/**
 * 批量保存消息（用于初始化加载）
 */
export async function saveMessages(accountId, conversationId, messages) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    // 先删除该会话的旧消息
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.bound([accountId, conversationId], [accountId, conversationId])
    const deleteRequest = index.openCursor(range)
    deleteRequest.onsuccess = (event) => {
      const cursor = event.target.result
      if (cursor) {
        cursor.delete()
        cursor.continue()
      }
    }

    // 写入新消息
    messages.forEach((msg, i) => {
      const id = messageId(accountId, conversationId, i)
      store.put({
        id,
        accountId,
        conversationId,
        message: msg,
        index: i,
        timestamp: msg.time || msg.timestamp || Date.now()
      })
    })

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/**
 * 获取会话的所有消息
 */
export async function getMessages(accountId, conversationId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.bound([accountId, conversationId], [accountId, conversationId])
    const request = index.getAll(range)

    request.onsuccess = () => {
      const results = request.result || []
      // 按 index 排序
      results.sort((a, b) => a.index - b.index)
      resolve(results.map(r => r.message))
    }
    request.onerror = () => reject(request.error)
  })
}

/**
 * 追加单条消息到会话末尾
 */
export async function appendMessage(accountId, conversationId, message) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.bound([accountId, conversationId], [accountId, conversationId])

    // 获取当前最大 index
    const countRequest = index.count(range)
    countRequest.onsuccess = () => {
      const nextIndex = countRequest.result
      const id = messageId(accountId, conversationId, nextIndex)
      store.put({
        id,
        accountId,
        conversationId,
        message,
        index: nextIndex,
        timestamp: message.time || message.timestamp || Date.now()
      })
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/**
 * 删除会话的所有消息
 */
export async function clearConversation(accountId, conversationId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.bound([accountId, conversationId], [accountId, conversationId])

    const deleteRequest = index.openCursor(range)
    deleteRequest.onsuccess = (event) => {
      const cursor = event.target.result
      if (cursor) {
        cursor.delete()
        cursor.continue()
      }
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/**
 * 清除用户的所有消息
 */
export async function clearAllMessages(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('accountId')
    const range = IDBKeyRange.bound(accountId, accountId)

    const deleteRequest = index.openCursor(range)
    deleteRequest.onsuccess = (event) => {
      const cursor = event.target.result
      if (cursor) {
        cursor.delete()
        cursor.continue()
      }
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

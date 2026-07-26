/**
 * 本地消息数据库（IndexedDB）
 *
 * 升级特性：
 * - 版本 2：添加 seq_ids 和 pending_messages store
 * - 消息上限：每会话 1000 条
 * - SeqId 持久化（从 localStorage 迁移到 IndexedDB）
 * - 离线队列（带重试和状态）
 */

const DB_NAME = 'charroom_messages'
const DB_VERSION = 2

function openDB() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onerror = () => reject(request.error)
    request.onsuccess = () => resolve(request.result)

    request.onupgradeneeded = (event) => {
      const db = event.target.result

      // 消息 store（已存在，v1）
      if (!db.objectStoreNames.contains('messages')) {
        const store = db.createObjectStore('messages', { keyPath: 'id' })
        store.createIndex('accountId', 'accountId', { unique: false })
        store.createIndex('conversationId', 'conversationId', { unique: false })
        store.createIndex('accountId_conversationId', ['accountId', 'conversationId'], { unique: false })
      }

      // SeqId store（v2 新增）
      if (!db.objectStoreNames.contains('seq_ids')) {
        const seqStore = db.createObjectStore('seq_ids', { keyPath: ['accountId', 'conversationId'] })
        seqStore.createIndex('accountId', 'accountId', { unique: false })
      }

      // 离线队列 store（v2 新增）
      if (!db.objectStoreNames.contains('pending_messages')) {
        const pendingStore = db.createObjectStore('pending_messages', { keyPath: 'messageId' })
        pendingStore.createIndex('status', 'status', { unique: false })
        pendingStore.createIndex('accountId', 'accountId', { unique: false })
      }
    }
  })
}

// ── 消息操作 ──────────────────────────────────────

const MAX_MESSAGES_PER_CONVERSATION = 1000

export async function saveMessage(accountId, conversationId, message, index) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readwrite')
    const store = tx.objectStore('messages')
    const id = `${accountId}:${conversationId}:${index}`
    store.put({ ...message, id, accountId, conversationId, index, timestamp: message.timestamp || Date.now() })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function saveMessages(accountId, conversationId, messages) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readwrite')
    const store = tx.objectStore('messages')

    // 先删除该会话的所有旧消息
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.only([accountId, conversationId])
    const deleteRequest = index.openCursor(range)

    deleteRequest.onsuccess = (event) => {
      const cursor = event.target.result
      if (cursor) {
        cursor.delete()
        cursor.continue()
      } else {
        // 删除完成后插入新消息
        messages.forEach((msg, i) => {
          const id = `${accountId}:${conversationId}:${i}`
          store.put({ ...msg, id, accountId, conversationId, index: i, timestamp: msg.timestamp || Date.now() })
        })
      }
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function appendMessage(accountId, conversationId, message) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readwrite')
    const store = tx.objectStore('messages')
    const index = store.index('accountId_conversationId')

    // 获取当前消息数量
    const countRequest = index.count(IDBKeyRange.only([accountId, conversationId]))

    countRequest.onsuccess = () => {
      const count = countRequest.result
      const newIndex = count
      const id = `${accountId}:${conversationId}:${newIndex}`
      store.put({ ...message, id, accountId, conversationId, index: newIndex, timestamp: message.timestamp || Date.now() })

      // 如果超出上限，删除最旧的消息
      if (count >= MAX_MESSAGES_PER_CONVERSATION) {
        const deleteCount = count - MAX_MESSAGES_PER_CONVERSATION + 1
        let deleted = 0
        const cursorRequest = index.openCursor(IDBKeyRange.only([accountId, conversationId]))
        cursorRequest.onsuccess = (e) => {
          const cursor = e.target.result
          if (cursor && deleted < deleteCount) {
            cursor.delete()
            deleted++
            cursor.continue()
          }
        }
      }
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function getMessages(accountId, conversationId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readonly')
    const store = tx.objectStore('messages')
    const index = store.index('accountId_conversationId')
    const request = index.getAll(IDBKeyRange.only([accountId, conversationId]))

    request.onsuccess = () => {
      const messages = request.result || []
      messages.sort((a, b) => (a.index || 0) - (b.index || 0))
      resolve(messages)
    }
    request.onerror = () => reject(request.error)
  })
}

export async function clearConversation(accountId, conversationId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readwrite')
    const store = tx.objectStore('messages')
    const index = store.index('accountId_conversationId')
    const range = IDBKeyRange.only([accountId, conversationId])
    const request = index.openCursor(range)

    request.onsuccess = (event) => {
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

export async function clearAllMessages(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('messages', 'readwrite')
    const store = tx.objectStore('messages')
    const index = store.index('accountId')
    const range = IDBKeyRange.only(accountId)
    const request = index.openCursor(range)

    request.onsuccess = (event) => {
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

// ── SeqId 操作（v2 新增）──────────────────────────

export async function getSeqId(accountId, conversationId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('seq_ids', 'readonly')
    const store = tx.objectStore('seq_ids')
    const request = store.get([accountId, conversationId])

    request.onsuccess = () => {
      const result = request.result
      resolve(result ? result.seqId : 0)
    }
    request.onerror = () => reject(request.error)
  })
}

export async function setSeqId(accountId, conversationId, seqId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('seq_ids', 'readwrite')
    const store = tx.objectStore('seq_ids')
    store.put({ accountId, conversationId, seqId })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function getAllSeqIds(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('seq_ids', 'readonly')
    const store = tx.objectStore('seq_ids')
    const index = store.index('accountId')
    const request = index.getAll(IDBKeyRange.only(accountId))

    request.onsuccess = () => {
      const results = request.result || []
      const map = {}
      results.forEach(r => { map[r.conversationId] = r.seqId })
      resolve(map)
    }
    request.onerror = () => reject(request.error)
  })
}

export async function clearSeqIds(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('seq_ids', 'readwrite')
    const store = tx.objectStore('seq_ids')
    const index = store.index('accountId')
    const range = IDBKeyRange.only(accountId)
    const request = index.openCursor(range)

    request.onsuccess = (event) => {
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

// ── 离线队列操作（v2 新增）────────────────────────

export async function saveToOfflineQueue(accountId, message) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readwrite')
    const store = tx.objectStore('pending_messages')
    store.put({
      ...message,
      accountId,
      status: 'pending',
      retryCount: 0,
      createdAt: Date.now()
    })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function getOfflineQueue(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readonly')
    const store = tx.objectStore('pending_messages')
    const index = store.index('status')
    const request = index.getAll(IDBKeyRange.only('pending'))

    request.onsuccess = () => {
      const results = request.result || []
      resolve(results.filter(r => r.accountId === accountId))
    }
    request.onerror = () => reject(request.error)
  })
}

export async function removeFromOfflineQueue(messageId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readwrite')
    const store = tx.objectStore('pending_messages')
    store.delete(messageId)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function updateOfflineMessageStatus(messageId, status, retryCount) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readwrite')
    const store = tx.objectStore('pending_messages')
    const request = store.get(messageId)

    request.onsuccess = () => {
      const msg = request.result
      if (msg) {
        msg.status = status
        if (retryCount !== undefined) msg.retryCount = retryCount
        store.put(msg)
      }
    }

    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export async function clearOfflineQueue(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readwrite')
    const store = tx.objectStore('pending_messages')
    const index = store.index('accountId')
    const range = IDBKeyRange.only(accountId)
    const request = index.openCursor(range)

    request.onsuccess = (event) => {
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

export async function getOfflineQueueSize(accountId) {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction('pending_messages', 'readonly')
    const store = tx.objectStore('pending_messages')
    const index = store.index('accountId')
    const request = index.count(IDBKeyRange.only(accountId))

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

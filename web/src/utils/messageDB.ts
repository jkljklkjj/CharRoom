import type { ChatMessage, Id } from '../types/chat'

const DB_NAME = 'charroom_messages'
// v2: 复合排序索引；v3: 会话元数据；v4: 兼容历史分支同版本号不同 schema
// （旧本地分支也用过 version 3 但索引名不同，同版本不会触发 upgrade，必须提升版本号）
const DB_VERSION = 4
const STORE_NAME = 'messages'
const META_STORE_NAME = 'conversation_meta'
const ORDER_INDEX = 'account_conversation_index'
interface StoredMessage {
  id: string; accountId: string; conversationId: string
  message: ChatMessage; index: number; timestamp: string | number
}
export interface ConversationMeta {
  id: string; accountId: string; conversationId: string
  lastIncomingMessageTime: number
}
export interface MessagePage { messages: ChatMessage[]; beforeIndex: number | null; hasMore: boolean }
let databasePromise: Promise<IDBDatabase> | null = null
function openDB(): Promise<IDBDatabase> {
  databasePromise ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onerror = () => { databasePromise = null; reject(request.error) }
    request.onsuccess = () => {
      const db = request.result
      db.onversionchange = () => { db.close(); databasePromise = null }
      resolve(db)
    }
    request.onupgradeneeded = () => {
      const db = request.result
      const store = db.objectStoreNames.contains(STORE_NAME)
        ? request.transaction!.objectStore(STORE_NAME)
        : db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      if (!store.indexNames.contains('accountId')) store.createIndex('accountId', 'accountId')
      if (!store.indexNames.contains('conversationId')) store.createIndex('conversationId', 'conversationId')
      if (!store.indexNames.contains('accountId_conversationId')) store.createIndex('accountId_conversationId', ['accountId', 'conversationId'])
      if (!store.indexNames.contains(ORDER_INDEX)) store.createIndex(ORDER_INDEX, ['accountId', 'conversationId', 'index'])
      // v3: 会话元数据（预览用最后消息时间），避免重建预览时全量读消息
      if (!db.objectStoreNames.contains(META_STORE_NAME)) {
        const meta = db.createObjectStore(META_STORE_NAME, { keyPath: 'id' })
        meta.createIndex('accountId', 'accountId', { unique: false })
      }
      // Older clients wrote both numeric and string IDs. Normalize existing
      // rows in the upgrade transaction before querying the new ordered index.
      const cursorRequest = store.openCursor()
      cursorRequest.onsuccess = () => {
        const cursor = cursorRequest.result
        if (!cursor) return
        const row = cursor.value as StoredMessage
        if (typeof row.accountId !== 'string' || typeof row.conversationId !== 'string') {
          cursor.update({ ...row, accountId: String(row.accountId), conversationId: String(row.conversationId) })
        }
        cursor.continue()
      }
    }
  })
  return databasePromise
}
/** 提取消息时间戳（兼容 time / timestamp / ISO 字符串） */
function messageTimestamp(message: ChatMessage): number {
  const v = message.time ?? message.timestamp ?? Date.now()
  if (typeof v === 'number') return v
  const parsed = Date.parse(String(v))
  return Number.isNaN(parsed) ? Date.now() : parsed
}
function range(accountId: Id, conversationId: Id) {
  return IDBKeyRange.only([String(accountId), String(conversationId)])
}
function orderedRange(accountId: Id, conversationId: Id, beforeIndex: number | null = null) {
  return IDBKeyRange.bound([String(accountId), String(conversationId), 0],
    [String(accountId), String(conversationId), beforeIndex ?? Number.MAX_SAFE_INTEGER], false, beforeIndex !== null)
}
async function write(operation: (store: IDBObjectStore) => void): Promise<void> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    operation(tx.objectStore(STORE_NAME))
  })
}
function record(accountId: Id, conversationId: Id, message: ChatMessage, index: number): StoredMessage {
  return { id: `${accountId}:${conversationId}:${index}`, accountId: String(accountId), conversationId: String(conversationId),
    // Vue reactive proxies cannot be cloned by IndexedDB.
    message: JSON.parse(JSON.stringify(message)) as ChatMessage, index, timestamp: messageTimestamp(message) }
}
function metaRecord(accountId: Id, conversationId: Id, lastTime: number) {
  return { id: `${accountId}:${conversationId}`, accountId: String(accountId), conversationId: String(conversationId), lastIncomingMessageTime: lastTime }
}
function touchConversationMeta(tx: IDBTransaction, accountId: Id, conversationId: Id, messageTime: number) {
  const metaStore = tx.objectStore(META_STORE_NAME)
  const metaId = `${accountId}:${conversationId}`
  const metaGet = metaStore.get(metaId)
  metaGet.onsuccess = () => {
    const meta = metaGet.result as ConversationMeta | undefined
    metaStore.put(metaRecord(accountId, conversationId, Math.max(meta?.lastIncomingMessageTime || 0, messageTime)))
  }
}
export function saveMessage(accountId: Id, conversationId: Id, message: ChatMessage, index: number) {
  const db = openDB()
  return db.then(database => new Promise<void>((resolve, reject) => {
    const tx = database.transaction([STORE_NAME, META_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    tx.objectStore(STORE_NAME).put(record(accountId, conversationId, message, index))
    touchConversationMeta(tx, accountId, conversationId, messageTimestamp(message))
  }))
}
export function saveMessages(accountId: Id, conversationId: Id, messages: readonly ChatMessage[]) {
  const db = openDB()
  return db.then(database => new Promise<void>((resolve, reject) => {
    const tx = database.transaction([STORE_NAME, META_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    const store = tx.objectStore(STORE_NAME)
    const request = store.index('accountId_conversationId').openCursor(range(accountId, conversationId))
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor) { cursor.delete(); cursor.continue() }
      else messages.forEach((message, index) => store.put(record(accountId, conversationId, message, index)))
    }
    // 同步会话元数据（最后消息时间取批内最大值）
    const maxTime = messages.reduce((max, message) => Math.max(max, messageTimestamp(message)), 0)
    if (maxTime > 0) {
      tx.objectStore(META_STORE_NAME).put(metaRecord(accountId, conversationId, maxTime))
    }
  }))
}
export async function getMessages(accountId: Id, conversationId: Id): Promise<ChatMessage[]> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE_NAME).objectStore(STORE_NAME).index(ORDER_INDEX).getAll(orderedRange(accountId, conversationId))
    request.onsuccess = () => resolve((request.result as StoredMessage[]).map(row => row.message))
    request.onerror = () => reject(request.error)
  })
}
export async function getMessagesPage(accountId: Id, conversationId: Id, beforeIndex: number | null = null, limit = 50): Promise<MessagePage> {
  if (limit <= 0 || beforeIndex === 0) return { messages: [], beforeIndex: null, hasMore: false }
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const rows: StoredMessage[] = []
    const request = db.transaction(STORE_NAME).objectStore(STORE_NAME).index(ORDER_INDEX).openCursor(orderedRange(accountId, conversationId, beforeIndex), 'prev')
    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor && rows.length < limit) { rows.push(cursor.value as StoredMessage); cursor.continue(); return }
      rows.reverse()
      resolve({ messages: rows.map(row => row.message), beforeIndex: rows[0]?.index ?? null, hasMore: !!cursor })
    }
  })
}
export function appendMessage(accountId: Id, conversationId: Id, message: ChatMessage) {
  const db = openDB()
  return db.then(database => new Promise<void>((resolve, reject) => {
    const tx = database.transaction([STORE_NAME, META_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    const store = tx.objectStore(STORE_NAME)
    const request = store.index(ORDER_INDEX).openCursor(orderedRange(accountId, conversationId), 'prev')
    request.onsuccess = () => {
      const last = request.result?.value as StoredMessage | undefined
      store.put(record(accountId, conversationId, message, last ? last.index + 1 : 0))
    }
    touchConversationMeta(tx, accountId, conversationId, messageTimestamp(message))
  }))
}
function deleteRows(index: string, key: IDBKeyRange, match: (row: StoredMessage) => boolean = () => true) {
  return write(store => {
    const request = store.index(index).openCursor(key)
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor) { if (match(cursor.value as StoredMessage)) cursor.delete(); cursor.continue() }
    }
  })
}
export function deleteMessage(accountId: Id, conversationId: Id, messageId: string) {
  return deleteRows('accountId_conversationId', range(accountId, conversationId), row => row.message.messageId === messageId)
}
export function clearConversation(accountId: Id, conversationId: Id) {
  const cleared = deleteRows('accountId_conversationId', range(accountId, conversationId))
  return cleared.then(() => new Promise<void>((resolve, reject) => {
    openDB().then(database => {
      const tx = database.transaction(META_STORE_NAME, 'readwrite')
      tx.oncomplete = () => resolve()
      tx.onerror = tx.onabort = () => reject(tx.error)
      tx.objectStore(META_STORE_NAME).delete(`${accountId}:${conversationId}`)
    }).catch(reject)
  }))
}
export function clearAllMessages(accountId: Id) {
  const cleared = deleteRows('accountId', IDBKeyRange.only(String(accountId)))
  return cleared.then(() => new Promise<void>((resolve, reject) => {
    const db = openDB()
    db.then(database => {
      const tx = database.transaction(META_STORE_NAME, 'readwrite')
      tx.oncomplete = () => resolve()
      tx.onerror = tx.onabort = () => reject(tx.error)
      const request = tx.objectStore(META_STORE_NAME).index('accountId').openCursor(IDBKeyRange.only(String(accountId)))
      request.onsuccess = () => {
        const cursor = request.result
        if (cursor) { cursor.delete(); cursor.continue() }
      }
    }).catch(reject)
  }))
}
/**
 * 获取账号下所有会话的元数据（会话预览用，避免全量读消息）。
 */
export async function getAllConversationMeta(accountId: Id): Promise<ConversationMeta[]> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(META_STORE_NAME).objectStore(META_STORE_NAME).index('accountId').getAll(IDBKeyRange.only(String(accountId)))
    request.onsuccess = () => resolve((request.result || []) as ConversationMeta[])
    request.onerror = () => reject(request.error)
  })
}
/**
 * 获取单个会话的元数据。
 */
export async function getConversationMeta(accountId: Id, conversationId: Id): Promise<ConversationMeta | null> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(META_STORE_NAME).objectStore(META_STORE_NAME).get(`${accountId}:${conversationId}`)
    request.onsuccess = () => resolve((request.result || null) as ConversationMeta | null)
    request.onerror = () => reject(request.error)
  })
}

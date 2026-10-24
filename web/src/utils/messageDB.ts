import type { ChatMessage, Id } from '../types/chat'

const DB_NAME = 'charroom_messages'
const STORE_NAME = 'messages'
const ORDER_INDEX = 'account_conversation_index'
interface StoredMessage {
  id: string; accountId: string; conversationId: string
  message: ChatMessage; index: number; timestamp: string | number
}
export interface MessagePage { messages: ChatMessage[]; beforeIndex: number | null; hasMore: boolean }
let databasePromise: Promise<IDBDatabase> | null = null
function openDB(): Promise<IDBDatabase> {
  databasePromise ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 2)
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
    message: JSON.parse(JSON.stringify(message)) as ChatMessage, index, timestamp: message.time || message.timestamp || Date.now() }
}
export function saveMessage(accountId: Id, conversationId: Id, message: ChatMessage, index: number) {
  return write(store => { store.put(record(accountId, conversationId, message, index)) })
}
export function saveMessages(accountId: Id, conversationId: Id, messages: readonly ChatMessage[]) {
  return write(store => {
    const request = store.index('accountId_conversationId').openCursor(range(accountId, conversationId))
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor) { cursor.delete(); cursor.continue() }
      else messages.forEach((message, index) => store.put(record(accountId, conversationId, message, index)))
    }
  })
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
  return write(store => {
    const request = store.index(ORDER_INDEX).openCursor(orderedRange(accountId, conversationId), 'prev')
    request.onsuccess = () => {
      const last = request.result?.value as StoredMessage | undefined
      store.put(record(accountId, conversationId, message, last ? last.index + 1 : 0))
    }
  })
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
  return deleteRows('accountId_conversationId', range(accountId, conversationId))
}
export function clearAllMessages(accountId: Id) { return deleteRows('accountId', IDBKeyRange.only(String(accountId))) }

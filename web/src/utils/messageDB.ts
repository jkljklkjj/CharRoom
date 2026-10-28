import type { ChatMessage, Id } from '../types/chat'
import { conversationPreviewText } from './format'

const DB_NAME = 'charroom_messages'
// Current indexed schema; older row formats are not imported.
const DB_VERSION = 7
const STORE_NAME = 'messages'
const META_STORE_NAME = 'conversation_meta'
const CURSOR_STORE_NAME = 'sync_cursors'
const ORDER_INDEX = 'account_conversation_index'
const TIME_INDEX = 'account_conversation_time_id'
const MESSAGE_ID_INDEX = 'account_conversation_message_id'
interface StoredMessage {
  id: string; accountId: string; conversationId: string
  message: ChatMessage; index: number; timestamp: number; sortId: string
}
export interface ConversationMeta {
  id: string; accountId: string; conversationId: string
  lastIncomingMessageTime: number
  /** 最新一条消息的预览文本 */
  lastMessagePreview?: string
}
export type MessageCursor = [number, string, string]
export interface MessagePage { messages: ChatMessage[]; beforeIndex: MessageCursor | null; hasMore: boolean }
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
      if(!db.objectStoreNames.contains(CURSOR_STORE_NAME)) {
        const cursors=db.createObjectStore(CURSOR_STORE_NAME,{keyPath:['accountId','conversationId']})
        cursors.createIndex('accountId','accountId')
      }
      const store = db.objectStoreNames.contains(STORE_NAME)
        ? request.transaction!.objectStore(STORE_NAME)
        : db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      if (!store.indexNames.contains('accountId')) store.createIndex('accountId', 'accountId')
      if (!store.indexNames.contains('conversationId')) store.createIndex('conversationId', 'conversationId')
      if (!store.indexNames.contains('accountId_conversationId')) store.createIndex('accountId_conversationId', ['accountId', 'conversationId'])
      if (!store.indexNames.contains(ORDER_INDEX)) store.createIndex(ORDER_INDEX, ['accountId', 'conversationId', 'index'])
      if (!store.indexNames.contains(MESSAGE_ID_INDEX)) store.createIndex(MESSAGE_ID_INDEX, ['accountId', 'conversationId', 'message.messageId'])
      if (!store.indexNames.contains(TIME_INDEX)) store.createIndex(TIME_INDEX, ['accountId', 'conversationId', 'timestamp', 'sortId', 'id'])
      // v3: 会话元数据（预览用最后消息时间），避免重建预览时全量读消息
      if (!db.objectStoreNames.contains(META_STORE_NAME)) {
        const meta = db.createObjectStore(META_STORE_NAME, { keyPath: 'id' })
        meta.createIndex('accountId', 'accountId', { unique: false })
      }

    }
  })
  return databasePromise
}
/** 提取消息时间戳（兼容 time / timestamp / ISO 字符串） */
function messageTimestamp(message: ChatMessage, fallback = Date.now()): number {
  const v = message.time ?? message.timestamp ?? fallback
  if (typeof v === 'number') return Number.isFinite(v) ? Math.max(0, v) : fallback
  if (/^\d+$/.test(v)) return Number(v)
  const parsed = Date.parse(String(v))
  return Number.isNaN(parsed) ? fallback : Math.max(0, parsed)
}
function range(accountId: Id, conversationId: Id) {
  return IDBKeyRange.only([String(accountId), String(conversationId)])
}
function orderedRange(accountId: Id, conversationId: Id, before: MessageCursor | null = null) {
  return IDBKeyRange.bound([String(accountId), String(conversationId), 0, '', ''],
    [String(accountId), String(conversationId), ...(before ?? [Number.MAX_SAFE_INTEGER, '\uffff', '\uffff'])], false, before !== null)
}
function allocationRange(accountId: Id, conversationId: Id) {
  return IDBKeyRange.bound([String(accountId), String(conversationId), 0],
    [String(accountId), String(conversationId), Number.MAX_SAFE_INTEGER])
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
function record(accountId: Id, conversationId: Id, message: ChatMessage, index: number, fallback = Date.now()): StoredMessage {
  return { id: `${accountId}:${conversationId}:${index}`, accountId: String(accountId), conversationId: String(conversationId),
    // Vue reactive proxies cannot be cloned by IndexedDB.
    message: JSON.parse(JSON.stringify(message)) as ChatMessage, index, timestamp: messageTimestamp(message, fallback),
    sortId: message.messageId && /^\d+$/.test(message.messageId) ? `n:${message.messageId.padStart(32, '0')}` : `s:${String(index).padStart(20, '0')}` }
}
function metaRecord(accountId: Id, conversationId: Id, lastTime: number) {
  return { id: `${accountId}:${conversationId}`, accountId: String(accountId), conversationId: String(conversationId), lastIncomingMessageTime: lastTime }
}
function touchConversationMeta(tx: IDBTransaction, accountId: Id, conversationId: Id, message: ChatMessage) {
  const messageTime = messageTimestamp(message)
  const metaStore = tx.objectStore(META_STORE_NAME)
  const metaId = `${accountId}:${conversationId}`
  const metaGet = metaStore.get(metaId)
  metaGet.onsuccess = () => {
    const meta = metaGet.result as ConversationMeta | undefined
    const previousTime = meta?.lastIncomingMessageTime || 0
    // 只有这条消息不早于已有记录时才覆盖预览，避免乱序写入把预览改回旧消息
    const lastMessagePreview = messageTime >= previousTime
      ? conversationPreviewText(message)
      : (meta?.lastMessagePreview || '')
    metaStore.put({
      ...metaRecord(accountId, conversationId, Math.max(previousTime, messageTime)),
      lastMessagePreview
    })
  }
}
export function saveMessage(accountId: Id, conversationId: Id, message: ChatMessage, index: number) {
  const db = openDB()
  return db.then(database => new Promise<void>((resolve, reject) => {
    const tx = database.transaction([STORE_NAME, META_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    tx.objectStore(STORE_NAME).put(record(accountId, conversationId, message, index))
    touchConversationMeta(tx, accountId, conversationId, message)
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
    // 同步会话元数据（最后消息时间与预览都取批内最新一条）
    const newest = messages.reduce<ChatMessage | null>((best, message) => (
      !best || messageTimestamp(message) >= messageTimestamp(best) ? message : best
    ), null)
    if (newest && messageTimestamp(newest) > 0) {
      tx.objectStore(META_STORE_NAME).put({
        ...metaRecord(accountId, conversationId, messageTimestamp(newest)),
        lastMessagePreview: conversationPreviewText(newest)
      })
    }
  }))
}
export async function getMessages(accountId: Id, conversationId: Id): Promise<ChatMessage[]> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE_NAME).objectStore(STORE_NAME).index(TIME_INDEX).getAll(orderedRange(accountId, conversationId))
    request.onsuccess = () => resolve((request.result as StoredMessage[]).map(row => row.message))
    request.onerror = () => reject(request.error)
  })
}
export async function getMessagesPage(accountId: Id, conversationId: Id, beforeIndex: MessageCursor | null = null, limit = 50): Promise<MessagePage> {
  if (limit <= 0) return { messages: [], beforeIndex: null, hasMore: false }
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const rows: StoredMessage[] = []
    const request = db.transaction(STORE_NAME).objectStore(STORE_NAME).index(TIME_INDEX).openCursor(orderedRange(accountId, conversationId, beforeIndex), 'prev')
    request.onerror = () => reject(request.error)
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor && rows.length < limit) { rows.push(cursor.value as StoredMessage); cursor.continue(); return }
      rows.reverse()
      resolve({ messages: rows.map(row => row.message), beforeIndex: rows[0] ? [rows[0].timestamp, rows[0].sortId, rows[0].id] : null, hasMore: !!cursor })
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
    const append = () => {
      const request = store.index(ORDER_INDEX).openCursor(allocationRange(accountId, conversationId), 'prev')
      request.onsuccess = () => {
        const last = request.result?.value as StoredMessage | undefined
        store.put(record(accountId, conversationId, message, last ? last.index + 1 : 0))
      }
    }
    if (message.messageId) {
      const existing = store.index(MESSAGE_ID_INDEX).get([String(accountId), String(conversationId), message.messageId])
      existing.onsuccess = () => {
        const row = existing.result as StoredMessage | undefined
        if (row) store.put(record(accountId, conversationId, { ...row.message, ...message }, row.index, row.timestamp))
        else append()
      }
    } else append()
    touchConversationMeta(tx, accountId, conversationId, message)
  }))
}

/** One indexed lookup when a bounded window changes its oldest displayed row. */
export async function getMessageCursor(accountId:Id,conversationId:Id,messageId:string):Promise<MessageCursor|null>{
  const db=await openDB()
  return new Promise((resolve,reject)=>{
    const request=db.transaction(STORE_NAME).objectStore(STORE_NAME).index(MESSAGE_ID_INDEX).get([String(accountId),String(conversationId),messageId])
    request.onsuccess=()=>{const row=request.result as StoredMessage|undefined;resolve(row?[row.timestamp,row.sortId,row.id]:null)}
    request.onerror=()=>reject(request.error)
  })
}
/** Batch upsert by message ID: no full conversation scan, renumbering or replacement. */
export interface AppliedCursor {conversationId:string;seqId:Id}
export async function mergeHistoryMessages(accountId: Id, conversationId: Id, messages: readonly ChatMessage[], applied?:AppliedCursor) {
  if(applied && (!/^\d+$/.test(String(applied.seqId)) || String(messages.at(-1)?.seqId)!==String(applied.seqId)))throw Error('Invalid applied cursor')
  const batch = new Map(messages.filter(m => m.messageId).map(m => [m.messageId!, m]))
  if (!batch.size) return
  const db = await openDB()
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction([STORE_NAME, META_STORE_NAME, CURSOR_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    const store = tx.objectStore(STORE_NAME)
    const last = store.index(ORDER_INDEX).openCursor(allocationRange(accountId, conversationId), 'prev')
    last.onsuccess = () => {
      let index = ((last.result?.value as StoredMessage | undefined)?.index ?? -1) + 1
      let remaining = batch.size, newest: ChatMessage | null = null
      for (const [id, message] of batch) {
        const request = store.index(MESSAGE_ID_INDEX).get([String(accountId), String(conversationId), id])
        request.onsuccess = () => {
          const old = request.result as StoredMessage | undefined
          // Local send status/live edits win over a bootstrap copy of the same ID.
          const merged = { ...message, ...old?.message }
          const row = record(accountId, conversationId, merged, old ? old.index : index++, old?.timestamp)
          store.put(row)
          if (!newest || row.timestamp >= messageTimestamp(newest)) newest = merged
          if (--remaining === 0 && newest) {
            touchConversationMeta(tx,accountId,conversationId,newest)
            if(applied) {
              const cursors=tx.objectStore(CURSOR_STORE_NAME),key=[String(accountId),applied.conversationId]
              const request=cursors.get(key)
              request.onsuccess=()=>{
                if(BigInt(request.result?.seqId||'0')<BigInt(applied.seqId))cursors.put({accountId:String(accountId),conversationId:applied.conversationId,seqId:String(applied.seqId)})
              }
            }
          }
        }
      }
    }
  })
}
/** Delete using primary keys only; never clone message bodies just to remove them. */
function deleteKeys(store: IDBObjectStore, index: string, key: IDBKeyRange) {
  const request = store.index(index).openKeyCursor(key)
  request.onsuccess = () => {
    const cursor = request.result
    if (cursor) { store.delete(cursor.primaryKey); cursor.continue() }
  }
}
/** Rename the optimistic row and merge a history copy in the same IndexedDB transaction. */
export async function confirmMessageId(accountId: Id, conversationId: Id, clientId: string,
                                       serverId: string, seqId: Id): Promise<void> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    const store = tx.objectStore(STORE_NAME)
    const index = store.index(MESSAGE_ID_INDEX)
    const oldRequest = index.get([String(accountId), String(conversationId), clientId])
    oldRequest.onsuccess = () => {
      const old = oldRequest.result as StoredMessage | undefined
      if (!old) return
      const canonicalRequest = index.get([String(accountId), String(conversationId), serverId])
      canonicalRequest.onsuccess = () => {
        const canonical = canonicalRequest.result as StoredMessage | undefined
        if (canonical && canonical.id !== old.id) store.delete(canonical.id)
        const message = { ...old.message, ...canonical?.message, messageId: serverId, seqId, isSent: 'sent' as const }
        store.put(record(accountId, conversationId, message, old.index, old.timestamp))
      }
    }
  })
}

export function deleteMessage(accountId: Id, conversationId: Id, messageId: string) {
  return write(store => {
    // Key cursors avoid cloning message bodies and only visit exact matches.
    // The index is non-unique, so remove every matching row in this transaction.
    deleteKeys(store, MESSAGE_ID_INDEX, IDBKeyRange.only([String(accountId), String(conversationId), messageId]))
  })
}
export async function clearConversation(accountId: Id, conversationId: Id) {
  const db = await openDB()
  return new Promise<void>((resolve, reject) => {
    // A delivery queued after this transaction must retain both its message and preview.
    const tx = db.transaction([STORE_NAME, META_STORE_NAME], 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    deleteKeys(tx.objectStore(STORE_NAME), 'accountId_conversationId', range(accountId, conversationId))
    tx.objectStore(META_STORE_NAME).delete(`${accountId}:${conversationId}`)
  })
}
export async function clearAllMessages(accountId: Id) {
  const db=await openDB()
  return new Promise<void>((resolve,reject)=>{
    const tx=db.transaction([STORE_NAME,META_STORE_NAME,CURSOR_STORE_NAME],'readwrite')
    tx.oncomplete=()=>resolve();tx.onerror=tx.onabort=()=>reject(tx.error)
    for(const name of [STORE_NAME,META_STORE_NAME,CURSOR_STORE_NAME]) {
      deleteKeys(tx.objectStore(name), 'accountId', IDBKeyRange.only(String(accountId)))
    }
  })
}
export async function getAppliedCursors(accountId:Id):Promise<Record<string,string>> {
  const db=await openDB()
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(CURSOR_STORE_NAME),request=tx.objectStore(CURSOR_STORE_NAME).index('accountId').getAll(String(accountId))
    tx.oncomplete=()=>resolve(Object.fromEntries(request.result.map(row=>[row.conversationId,String(row.seqId)])))
    tx.onerror=tx.onabort=()=>reject(tx.error)
  })
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

/**
 * 读取单个会话最新一条消息（倒序游标一步），用于元数据缺预览时的补写。
 */
export async function getLastMessage(accountId: Id, conversationId: Id): Promise<ChatMessage | null> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE_NAME).objectStore(STORE_NAME).index(TIME_INDEX)
      .openCursor(orderedRange(accountId, conversationId), 'prev')
    request.onsuccess = () => resolve((request.result?.value as StoredMessage | undefined)?.message ?? null)
    request.onerror = () => reject(request.error)
  })
}

/**
 * 合并写入会话预览文本（不改动时间），用于升级前旧元数据的补写。
 */
export function setConversationPreview(accountId: Id, conversationId: Id, preview: string) {
  const db = openDB()
  return db.then(database => new Promise<void>((resolve, reject) => {
    const tx = database.transaction(META_STORE_NAME, 'readwrite')
    tx.oncomplete = () => resolve()
    tx.onerror = tx.onabort = () => reject(tx.error)
    const store = tx.objectStore(META_STORE_NAME)
    const request = store.get(`${accountId}:${conversationId}`)
    request.onsuccess = () => {
      const meta = request.result as ConversationMeta | undefined
      if (!meta) return
      store.put({ ...meta, lastMessagePreview: preview })
    }
  }))
}

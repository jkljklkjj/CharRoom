import type { MessageWrapper } from '../proto/messages'
import type { SendOptions } from '../types/transport'
export const MAX_RETRIES = 3
/** 离线队列容量上限（条数），达到上限后新消息不再入队 */
export const MAX_QUEUE_SIZE = 1000
export interface QueueEntry {
  messageId: string; wrapperObj: MessageWrapper; buffer: number[] | Uint8Array<ArrayBuffer>
  opts: SendOptions; timestamp: number; retryCount: number
}
/**
 * PWA 离线消息队列（IndexedDB 持久化）。
 *
 * 当用户发送消息时网络不可用，消息存入 IndexedDB。
 * 网络恢复后自动重发并清理已发送的消息。
 */

const DB_NAME = 'chatlite_offline'
const DB_VERSION = 1
const STORE_NAME = 'pending_messages'

let db: IDBDatabase | null = null

/**
 * 打开 IndexedDB 连接（懒初始化）。
 */
function openDB(): Promise<IDBDatabase> {
  if (db) return Promise.resolve(db)
  return new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = (event) => {
      const database = request.result
      if (!database.objectStoreNames.contains(STORE_NAME)) {
        const store = database.createObjectStore(STORE_NAME, { keyPath: 'messageId' })
        store.createIndex('timestamp', 'timestamp', { unique: false })
      }
    }
    request.onsuccess = (event) => {
      db = request.result
      resolve(db)
    }
    request.onerror = (event) => {
      console.error('[OfflineQueue] IndexedDB 打开失败:', request.error)
      reject(request.error)
    }
  })
}

/**
 * 保存消息到离线队列。
 * @param {object} wrapperObj - protobuf wrapper 对象
 * @param {ArrayBuffer} buffer - 编码后的 protobuf 二进制
 * @param {object} opts - { streamType, conversationId }
 */
export async function saveToQueue(wrapperObj: MessageWrapper, buffer: ArrayBuffer, opts: SendOptions) {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    const entry: QueueEntry = {
      messageId: wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId || `offline_${Date.now()}`,
      wrapperObj,
      buffer: new Uint8Array(buffer), // IndexedDB stores typed arrays through structured clone
      opts,
      timestamp: Date.now(),
      retryCount: 0
    }

    return new Promise((resolve) => {
      // 队列容量上限检查，避免 PWA 离线期间 IndexedDB 无限增长
      const countRequest = store.count()
      countRequest.onsuccess = () => {
        if (countRequest.result >= MAX_QUEUE_SIZE) {
          console.warn(`[OfflineQueue] 队列已满(${MAX_QUEUE_SIZE})，丢弃消息:`, entry.messageId)
          resolve(false) // 队列满未保存，明确返回 false，不再误导调用方
          return
        }
        store.put(entry)
      }
      countRequest.onerror = () => resolve(false)
      tx.oncomplete = () => resolve(true)
      tx.onerror = (e) => {
        console.warn('[OfflineQueue] 保存失败:', tx.error)
        resolve(false)
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
export async function getAllPending(): Promise<QueueEntry[]> {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const request = store.getAll()

    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result || [])
      request.onerror = (e) => reject(tx.error)
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
export async function removeMessage(messageId: string) {
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
export async function getQueueSize(): Promise<number> {
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
export async function incrementRetry(messageId: string) {
  try {
    const database = await openDB()
    const tx = database.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const request = store.get(messageId)

    request.onsuccess = () => {
      const entry = request.result as QueueEntry | undefined
      if (entry) {
        entry.retryCount = (entry.retryCount || 0) + 1
        store.put(entry)
      }
    }
  } catch (e) {
    // ignore
  }
}

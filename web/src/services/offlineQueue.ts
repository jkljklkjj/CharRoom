import type { MessageWrapper } from '../proto/messages'
import type { SendOptions } from '../types/transport'
export const MAX_RETRIES = 3
export const MAX_QUEUE_SIZE = 1000
export interface QueueEntry {
  accountId: string; messageId: string; wrapperObj: MessageWrapper; buffer: Uint8Array<ArrayBuffer>
  opts: SendOptions; timestamp: number; nextAttemptAt: number; retryCount: number; state: 'pending' | 'sending' | 'failed'
}
const STORE = 'account_outbox'
let database: Promise<IDBDatabase> | null = null
function openDB() {
  database ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('chatlite_offline', 3)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: ['accountId', 'messageId'] })
        store.createIndex('account', 'accountId')
      }
      const store = request.transaction!.objectStore(STORE)
      if (!store.indexNames.contains('due')) store.createIndex('due', ['accountId', 'nextAttemptAt'])

    }
    request.onerror = () => { database = null; reject(request.error) }
    request.onsuccess = () => {
      const db = request.result
      db.onversionchange = () => { db.close(); database = null }
      resolve(db)
    }
  })
  return database
}
function owner(accountId: string) { if (!accountId) throw new Error('Outbox owner required'); return String(accountId) }
async function mutate<T>(operation: (store: IDBObjectStore, result: (value: T) => void) => void): Promise<T> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite')
    let value: T
    tx.oncomplete = () => resolve(value)
    tx.onerror = tx.onabort = () => reject(tx.error || new Error('Outbox transaction failed'))
    operation(tx.objectStore(STORE), result => { value = result })
  })
}
export async function saveToQueue(accountId: string, wrapperObj: MessageWrapper, buffer: ArrayBuffer, opts: SendOptions) {
  const account = owner(accountId), messageId = wrapperObj.chat?.messageId || wrapperObj.groupChat?.messageId
  if (!messageId) throw new Error('Stable message ID required')
  return mutate<boolean>((store, result) => {
    const existing = store.get([account, messageId])
    existing.onsuccess = () => {
      if (existing.result) { result(true); return }
      const count = store.index('account').count(IDBKeyRange.only(account))
      count.onsuccess = () => {
        if (count.result >= MAX_QUEUE_SIZE) { store.transaction.abort(); return }
        store.put({ accountId: account, messageId, wrapperObj: JSON.parse(JSON.stringify(wrapperObj)), buffer: new Uint8Array(buffer),
          opts: { ...opts }, timestamp: Date.now(), nextAttemptAt: 0, retryCount: 0, state: 'pending' } satisfies QueueEntry)
        result(true)
      }
    }
  })
}
export async function getAllPending(accountId: string): Promise<QueueEntry[]> {
  const account = owner(accountId), db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE).objectStore(STORE).index('account').getAll(IDBKeyRange.only(account))
    request.onsuccess = () => resolve((request.result as QueueEntry[]).sort((a, b) => a.timestamp - b.timestamp))
    request.onerror = () => reject(request.error)
  })
}
export async function removeMessage(accountId: string, messageId: string) {
  return mutate<void>(store => { store.delete([owner(accountId), messageId]) })
}
export async function getPending(accountId: string, messageId: string): Promise<QueueEntry | null> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE).objectStore(STORE).get([owner(accountId), messageId])
    request.onsuccess = () => resolve(request.result || null)
    request.onerror = () => reject(request.error)
  })
}
export async function getDuePending(accountId: string, now = Date.now(), limit = 32): Promise<QueueEntry[]> {
  const account = owner(accountId), db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE).objectStore(STORE).index('due')
      .getAll(IDBKeyRange.bound([account, 0], [account, now]), Math.max(1, Math.min(100, limit)))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}
async function update(accountId: string, messageId: string, change: (row: QueueEntry) => void) {
  return mutate<QueueEntry | null>((store, result) => {
    const request = store.get([owner(accountId), messageId])
    request.onsuccess = () => {
      const row = request.result as QueueEntry | undefined
      if (!row) { result(null); return }
      change(row); store.put(row); result(row)
    }
  })
}
export const markAttempt = (accountId: string, messageId: string) => update(accountId, messageId, row => {
  row.retryCount++; row.state = 'sending'; row.nextAttemptAt = Date.now() + 16000
})
export const markFailed = (accountId: string, messageId: string) => update(accountId, messageId, row => { row.state = 'failed'; row.nextAttemptAt = Number.MAX_SAFE_INTEGER })
export const retryMessage = (accountId: string, messageId: string) => update(accountId, messageId, row => { row.state = 'pending'; row.retryCount = 0; row.nextAttemptAt = 0 })
export async function clearQueue(accountId: string) {
  return mutate<void>(store => {
    const request = store.index('account').openCursor(IDBKeyRange.only(owner(accountId)))
    request.onsuccess = () => { const cursor = request.result; if (cursor) { cursor.delete(); cursor.continue() } }
  })
}
export async function getQueueSize(accountId: string): Promise<number> {
  const db = await openDB()
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE).objectStore(STORE).index('account').count(IDBKeyRange.only(owner(accountId)))
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

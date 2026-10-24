const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { IDBFactory, IDBKeyRange } = require('fake-indexeddb')
const { loadTs } = require('./helpers.cjs')
test('IndexedDB pagination preserves numeric order, deletion and account isolation', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  assert.equal(typeof db.getMessagesPage, 'function')
  assert.equal(typeof db.deleteMessage, 'function')
  for (let i = 0; i < 12; i++) await db.appendMessage('A', '41', { user: 'you', text: String(i), messageId: 'm' + i })
  await db.appendMessage('B', '41', { user: 'you', text: 'other account', messageId: 'm0' })
  const latest = await db.getMessagesPage('A', '41', null, 5)
  assert.deepEqual(Array.from(latest.messages, m => m.text), ['7', '8', '9', '10', '11'])
  assert.equal(latest.hasMore, true)
  const older = await db.getMessagesPage('A', '41', latest.beforeIndex, 10)
  assert.deepEqual(Array.from(older.messages, m => m.text), ['0', '1', '2', '3', '4', '5', '6'])
  assert.equal(older.hasMore, false)
  await db.deleteMessage('A', '41', 'm3')
  await db.appendMessage('A', '41', { user: 'you', text: '12', messageId: 'm12' })
  const all = await db.getMessages('A', '41')
  assert.equal(all.length, 12)
  assert.equal(all.at(-2).text, '11')
  await db.clearAllMessages('A')
  assert.equal((await db.getMessages('B', '41')).length, 1)
})
test('bulk replacement survives refresh without erasing its new rows', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  await db.saveMessages('A', '41', [{ user: 'you', text: 'old' }])
  await db.saveMessages('A', '41', [{ user: 'you', text: 'new' }, { user: '41', text: 'reply' }])
  assert.deepEqual(Array.from(await db.getMessages('A', '41'), m => m.text), ['new', 'reply'])
})
test('version 1 numeric account keys migrate without losing existing history', async () => {
  const indexedDB = new IDBFactory()
  await new Promise((resolve, reject) => {
    const request = indexedDB.open('charroom_messages', 1)
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore('messages', { keyPath: 'id' })
      store.createIndex('accountId', 'accountId')
      store.createIndex('conversationId', 'conversationId')
      store.createIndex('accountId_conversationId', ['accountId', 'conversationId'])
      store.put({ id: '1:41:0', accountId: 1, conversationId: 41, index: 0, message: { user: '41', text: 'legacy history' } })
    }
    request.onerror = () => reject(request.error)
    request.onsuccess = () => { request.result.close(); resolve() }
  })
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), { indexedDB, IDBKeyRange, structuredClone })
  assert.deepEqual(Array.from(await db.getMessages('1', '41'), m => m.text), ['legacy history'])
})

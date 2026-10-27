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
test('conversation meta tracks last message time and clears with conversation', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  await db.appendMessage('A', '41', { user: '41', text: 'first', time: 1000 })
  await db.appendMessage('A', '41', { user: 'you', text: 'second', time: 2000 })
  await db.appendMessage('A', 'group:7', { user: '9', text: 'group hi', time: 3000 })
  const all = await db.getAllConversationMeta('A')
  assert.equal(all.length, 2)
  const privateMeta = await db.getConversationMeta('A', '41')
  assert.equal(privateMeta.lastIncomingMessageTime, 2000)
  assert.equal((await db.getConversationMeta('A', 'group:7')).lastIncomingMessageTime, 3000)
  // 批量保存取批内最大时间
  await db.saveMessages('B', '41', [{ user: '41', text: 'x', time: 500 }, { user: '41', text: 'y', time: 900 }])
  assert.equal((await db.getConversationMeta('B', '41')).lastIncomingMessageTime, 900)
  // 清空会话时同步删除元数据
  await db.clearConversation('A', '41')
  assert.equal(await db.getConversationMeta('A', '41'), null)
  assert.equal((await db.getAllConversationMeta('A')).length, 1)
})
test('conversation meta keeps the newest message preview and supports backfill', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  await db.appendMessage('A', '41', { user: '41', text: '在吗？', time: 1000 })
  await db.appendMessage('A', '41', { user: 'you', text: '在的\n刚忙完', time: 2000 })
  // 乱序到达的旧消息不能把预览改回旧内容
  await db.appendMessage('A', '41', { user: '41', text: '旧消息', time: 500 })
  const meta = await db.getConversationMeta('A', '41')
  assert.equal(meta.lastMessagePreview, '在的 刚忙完')
  assert.equal(meta.lastIncomingMessageTime, 2000)
  // 升级前的元数据没有预览：用最新一条（按插入顺序）补写，不改时间
  await db.appendMessage('A', '42', { user: '42', text: '升级前的老会话', time: 1500 })
  assert.equal((await db.getLastMessage('A', '42')).text, '升级前的老会话')
  await db.setConversationPreview('A', '42', '补写后的预览')
  const backfilled = await db.getConversationMeta('A', '42')
  assert.equal(backfilled.lastMessagePreview, '补写后的预览')
  assert.equal(backfilled.lastIncomingMessageTime, 1500)
  // 无消息的会话返回 null，账号之间互不影响
  assert.equal(await db.getLastMessage('A', '99'), null)
  await db.appendMessage('B', '41', { user: '41', text: '另一个账号', time: 4000 })
  assert.equal((await db.getConversationMeta('A', '41')).lastMessagePreview, '在的 刚忙完')
  assert.equal((await db.getConversationMeta('B', '41')).lastMessagePreview, '另一个账号')
})
test('legacy branch v3 schema (accountId_conversationId_index) upgrades to the ordered index', async () => {
  // 旧本地分支曾用 version 3 + 'accountId_conversationId_index' 索引名，
  // 与当前代码同版本号但 schema 不同——upgrade 不会触发，必须靠版本提升修复
  const indexedDB = new IDBFactory()
  await new Promise((resolve, reject) => {
    const request = indexedDB.open('charroom_messages', 3)
    request.onupgradeneeded = () => {
      const db = request.result
      const store = db.createObjectStore('messages', { keyPath: 'id' })
      store.createIndex('accountId', 'accountId')
      store.createIndex('conversationId', 'conversationId')
      store.createIndex('accountId_conversationId', ['accountId', 'conversationId'])
      store.createIndex('accountId_conversationId_index', ['accountId', 'conversationId', 'index'])
      db.createObjectStore('conversation_meta', { keyPath: 'id' }).createIndex('accountId', 'accountId')
      store.put({ id: 'A:41:0', accountId: 'A', conversationId: '41', index: 0, message: { user: '41', text: 'legacy branch row' } })
    }
    request.onerror = () => reject(request.error)
    request.onsuccess = () => { request.result.close(); resolve() }
  })
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), { indexedDB, IDBKeyRange, structuredClone })
  // 旧数据可读
  assert.deepEqual(Array.from(await db.getMessages('A', '41'), m => m.text), ['legacy branch row'])
  // 排序索引/追加/分页可用（此前抛 NotFoundError: The specified index was not found）
  await db.appendMessage('A', '41', { user: 'you', text: 'appended', time: 1000 })
  const page = await db.getMessagesPage('A', '41', null, 10)
  assert.deepEqual(Array.from(page.messages, m => m.text), ['legacy branch row', 'appended'])
  assert.equal((await db.getConversationMeta('A', '41')).lastIncomingMessageTime, 1000)
})

test('history merge is idempotent, ordered and retains concurrent live messages and other accounts', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  const first = { user: '41', text: 'old', time: 1000, messageId: '9007199254740993' }
  const second = { ...first, text: 'same timestamp', messageId: '9007199254740994' }
  await db.appendMessage('A', '41', { ...first, time: 2000, text: 'live', messageId: '9007199254740995' })
  await db.appendMessage('B', '41', first)
  await Promise.all([db.mergeHistoryMessages('A', '41', [second, first]),
    db.appendMessage('A', '41', { ...first, time: 3000, text: 'during sync', messageId: '9007199254740996' })])
  await db.mergeHistoryMessages('A', '41', [first, second])
  await db.appendMessage('A', '41', first) // delayed live delivery of an already restored message
  assert.deepEqual(Array.from(await db.getMessages('A', '41'), m => m.text), ['old', 'same timestamp', 'live', 'during sync'])
  assert.equal((await db.getMessages('B', '41')).length, 1)
  const latest = await db.getMessagesPage('A', '41', null, 2)
  assert.deepEqual(Array.from(latest.messages, m => m.text), ['live', 'during sync'])
  assert.deepEqual(Array.from((await db.getMessagesPage('A', '41', latest.beforeIndex, 2)).messages, m => m.text), ['old', 'same timestamp'])
})

test('history insertion leaves existing pagination cursors stable and only writes incoming IDs', async () => {
  const { IDBObjectStore } = require('fake-indexeddb')
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), {
    indexedDB: new IDBFactory(), IDBKeyRange, structuredClone
  })
  for (const id of ['30', '40', '50']) await db.appendMessage('A', '41', { user: '41', text: id, messageId: id, time: Number(id) })
  const page = await db.getMessagesPage('A', '41', null, 2)
  let puts = 0, deletes = 0, fullReads = 0
  const original = { put: IDBObjectStore.prototype.put, delete: IDBObjectStore.prototype.delete, getAll: IDBObjectStore.prototype.getAll }
  IDBObjectStore.prototype.put = function (...args) { if (this.name === 'messages') puts++; return original.put.apply(this, args) }
  IDBObjectStore.prototype.delete = function (...args) { if (this.name === 'messages') deletes++; return original.delete.apply(this, args) }
  IDBObjectStore.prototype.getAll = function (...args) { if (this.name === 'messages') fullReads++; return original.getAll.apply(this, args) }
  try {
    await db.mergeHistoryMessages('A', '41', [{ user: '41', text: '10', messageId: '10', time: 10 }])
    assert.equal(puts, 1)
    assert.equal(deletes, 0)
    assert.equal(fullReads, 0)
  } finally { Object.assign(IDBObjectStore.prototype, original) }
  const older = await db.getMessagesPage('A', '41', page.beforeIndex, 10)
  assert.deepEqual(Array.from(older.messages, row => row.messageId), ['10', '30'])
})

test('version 5 history upgrades to timestamp/ID order without changing account ownership or send status', async () => {
  const indexedDB = new IDBFactory()
  await new Promise((resolve, reject) => {
    const request = indexedDB.open('charroom_messages', 5)
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore('messages', { keyPath: 'id' })
      for (const [index, time, id] of [[0, 3000, '9007199254740995'], [1, 1000, '9007199254740993'], [2, 1000, '9007199254740994']]) {
        store.put({ id: `A:41:${index}`, accountId: 'A', conversationId: '41', index, timestamp: time,
          message: { messageId: id, user: 'you', time, text: id, isSent: 'failed' } })
      }
    }
    request.onerror = () => reject(request.error)
    request.onsuccess = () => { request.result.close(); resolve() }
  })
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'), { indexedDB, IDBKeyRange, structuredClone })
  const rows = await db.getMessages('A', '41')
  assert.deepEqual(Array.from(rows, row => row.messageId), ['9007199254740993', '9007199254740994', '9007199254740995'])
  assert.ok(rows.every(row => row.isSent === 'failed'))
  assert.equal((await db.getMessages('B', '41')).length, 0)
})

const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { IDBFactory, IDBKeyRange } = require('fake-indexeddb')
const { loadTs } = require('./helpers.cjs')

async function fixture() {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'),
    { indexedDB: new IDBFactory(), IDBKeyRange, structuredClone },
    { '../utils/format': { conversationPreviewText: row => row.text }, './format': { conversationPreviewText: row => row.text } })
  return db
}
const own = (messageId, text, time) => ({ user: 'you', targetId: '2', text, time, messageId })

test('an acknowledged client ID keeps resolving after its visible row is gone', async () => {
  const db = await fixture()
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  // A second ACK for the same send (or a crash before the rename lands) must stay idempotent.
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  const rows = await db.getMessages('1', '2')
  assert.deepEqual(Array.from(rows, row => [row.messageId, row.seqId, row.isSent]), [['9001', '7', 'sent']])
})

test('a history copy still carrying the client ID merges into the persisted row', async () => {
  const db = await fixture()
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  // Late echo: the transport replays the pre-ACK copy which still names the client ID.
  await db.appendMessage('1', '2', own('local_a', 'hi', 1000))
  await db.mergeHistoryMessages('1', '2', [{ ...own('local_a', 'hi', 1000), seqId: '7' }], { conversationId: '1:2', seqId: '7' })
  const rows = await db.getMessages('1', '2')
  assert.deepEqual(Array.from(rows, row => row.messageId), ['9001'])
  assert.equal((await db.getAppliedCursors('1'))['1:2'], '7')
})

test('a persisted history row removes the placeholder it superseded', async () => {
  const db = await fixture()
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sent' })
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  // Simulate the window where the ACK recorded the alias but the rename never wrote.
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sent' })
  const before = await db.getMessages('1', '2')
  assert.ok(before.length >= 1)
  await db.mergeHistoryMessages('1', '2', [own('9001', 'hi', 1000)])
  const rows = await db.getMessages('1', '2')
  assert.deepEqual(Array.from(rows, row => row.messageId), ['9001'])
})

test('a stored placeholder page reads back as its persisted copy without duplicates', async () => {
  const db = await fixture()
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  // Force the crash state: alias present, row still stored under the client ID.
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sent' })
  const page = await db.getMessagesPage('1', '2', null, 50)
  const ids = Array.from(page.messages, row => row.messageId)
  assert.equal(new Set(ids).size, ids.length)
  assert.ok(ids.includes('9001'))
  assert.ok(!ids.includes('local_a'))
})

test('aliases never cross accounts or conversations', async () => {
  const db = await fixture()
  await db.appendMessage('1', '2', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  await db.confirmMessageId('1', '2', 'local_a', '9001', '7')
  await db.appendMessage('9', '2', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  await db.appendMessage('1', '3', { ...own('local_a', 'hi', 1000), isSent: 'sending' })
  assert.deepEqual(Array.from(await db.getMessages('9', '2'), row => row.messageId), ['local_a'])
  assert.deepEqual(Array.from(await db.getMessages('1', '3'), row => row.messageId), ['local_a'])
  await db.clearAllMessages('1')
  assert.deepEqual(Array.from(await db.getMessages('1', '2'), row => row.messageId), [])
  assert.deepEqual(Array.from(await db.getMessages('9', '2'), row => row.messageId), ['local_a'])
})

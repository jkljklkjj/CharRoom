const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { IDBFactory, IDBKeyRange } = require('fake-indexeddb')
const { loadTs } = require('./helpers.cjs')

test('canonical ACK atomically merges optimistic and already downloaded history rows within one account', async () => {
  const { exports: db } = await loadTs(path.resolve(__dirname, '../src/utils/messageDB.ts'),
    { indexedDB: new IDBFactory(), IDBKeyRange }, { '../utils/format': { conversationPreviewText: row => row.text }, './format': { conversationPreviewText: row => row.text } })
  const message = { user: 'you', targetId: '2', text: 'fixture', time: 1, isSent: 'sending' }
  await db.appendMessage('1', '2', { ...message, messageId: 'local_1' })
  await db.appendMessage('1', '2', { ...message, messageId: '9007199254740999' })
  await db.appendMessage('3', '2', { ...message, messageId: 'local_1' })
  await db.confirmMessageId('1', '2', 'local_1', '9007199254740999', '17')
  const rows = await db.getMessages('1', '2')
  assert.equal(rows.length, 1)
  assert.equal(rows[0].messageId, '9007199254740999')
  assert.equal(rows[0].seqId, '17')
  assert.equal(rows[0].isSent, 'sent')
  assert.equal((await db.getMessages('3', '2'))[0].messageId, 'local_1')
})

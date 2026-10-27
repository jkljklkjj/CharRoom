const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
async function fixture(read) {
  const state = { accountId: 'A', selectedChatId: null, messages: [], groupMessages: [], conversationSeqIds: {} }
  let version = 0
  const { exports: messages } = await loadTs(path.resolve(__dirname, '../src/store/messages.ts'), {}, {
    './state': { state, PAGE_SIZE: 50 }, './storage': { getPrivateConversationId: m => m.targetId, normalizeTimeValue: Number },
    './users': { updateConversationState() {}, invalidateConversationPreview() {} },
    '../utils/format': { conversationPreviewText: m => m.text }, dompurify: { default: { sanitize: t => t } },
    '../services/sessionEpoch': { sessionEpoch: { capture: () => { const captured = version; return { version, isCurrent: () => captured === version } } } },
    '../utils/messageDB': { getMessagesPage: read, appendMessage: async () => {}, mergeHistoryMessages: async () => {}, deleteMessage() {} }
  })
  return { state, messages, renew: () => { version++ } }
}
const page = id => ({ messages: [{ messageId: id, text: id }], beforeIndex: [1, id, id], hasMore: true })

test('late conversation A cannot overwrite B, including A→B→A', async () => {
  const first = deferred(); let calls = 0
  const { messages, state } = await fixture(async (_, id) => ++calls === 1 ? first.promise : page(id))
  const pending = messages.loadConversation('1')
  await messages.loadConversation('2')
  await messages.loadConversation('1')
  first.resolve(page('stale'))
  await pending
  assert.equal(state.messages[0].messageId, '1')
})

test('older-page requests coalesce and cannot prepend to another account', async () => {
  const older = deferred(); let calls = 0
  const { messages, state, renew } = await fixture(async () => ++calls === 1 ? page('new') : older.promise)
  await messages.loadConversation('1')
  const first = messages.loadOlderMessages(), second = messages.loadOlderMessages()
  assert.equal(first, second)
  renew(); state.accountId = 'B'; state.messages = []
  older.resolve(page('old-A'))
  assert.equal(await first, false)
  assert.equal(state.messages.length, 0)
  assert.equal(calls, 2)
})

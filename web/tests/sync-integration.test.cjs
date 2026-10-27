const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const { transpile, loadTs } = require('./helpers.cjs')

async function syncEntry(api, store) {
  const source = fs.readFileSync(path.resolve(__dirname, '../src/views/WebApp.vue'), 'utf8')
  const code = source.slice(source.indexOf('async function syncConversation('), source.indexOf('async function syncAllConversations('))
  const { exports: service } = fs.existsSync(path.resolve(__dirname, '../src/services/conversationSync.ts'))
    ? await loadTs(path.resolve(__dirname, '../src/services/conversationSync.ts')) : { exports: {} }
  const context = vm.createContext({ api, store, console, ...service, sessionEpoch: { capture: () => ({ version: 1, isCurrent: () => true }) },
    historyMessages: rows => rows, incrementalSync: undefined })
  // The shared coordinator is injected after it exists; the original function uses api/store directly.
  if (service.ConversationSync) context.incrementalSync = new service.ConversationSync({
    capture: () => ({ key: '1:1', isCurrent: () => true }), cursor: store.getConversationSeqId,
    fetch: api.syncMessages, apply: (_, rows) => store.restoreHistoryPage('1', rows), commit: store.setConversationSeqId
  })
  vm.runInContext(transpile(code) + '\nglobalThis.sync = syncConversation', context)
  return context.sync
}
test('view incremental sync drains hasMore and waits for durable batch writes', async () => {
  let cursor = '0', writes = 0, calls = 0
  const store = { state: { accountId: '1' }, getConversationSeqId: () => cursor,
    restoreHistoryPage: async () => { writes++ }, addMessage: async () => { writes++ },
    setConversationSeqId: (_, value) => { cursor = String(value) } }
  const api = { syncMessages: async () => { calls++; return { messages: [{ senderId: '2', id: String(calls), seqId: String(calls) }], nextSeqId: String(calls), hasMore: calls === 1 } } }
  await (await syncEntry(api, store))('user:1:2', '2')
  assert.equal(calls, 2)
  assert.equal(writes, 2)
  assert.equal(cursor, '2')
})

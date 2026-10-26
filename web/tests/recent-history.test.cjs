const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
function loginSync(api, store) {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/WebApp.vue'), 'utf8')
  const start = source.indexOf('async function syncAllConversations()')
  const end = source.indexOf('/** 建立 WebTransport', start)
  const code = ts.transpileModule(source.slice(start, end), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  const context = vm.createContext({ api, store, console, Date, Number, String, BigInt })
  vm.runInContext(code + '\n globalThis.sync = syncAllConversations', context)
  return context.sync
}
test('new device restores received, sent and group history over every page', async () => {
  const privateMessages = [], groups = [], cursors = [], requests = []
  const store = { state: { accountId: '1' }, restoreHistoryPage: async (_, messages) => messages.forEach(m => (m.groupId ? groups : privateMessages).push(m)),
    rebuildConversationStates: async () => {}, setConversationSeqId: (...a) => cursors.push(a) }
  const received = { id: '9007199254740993', senderId: '2', receiverId: '1', message: 'received', timestamp: 100,
    conversationId: 'user:1:2', seqId: '9' }
  const sent = { ...received, id: '9007199254740994', senderId: '1', receiverId: '2', message: 'sent' }
  const group = { ...received, id: '9007199254740995', receiverId: 'group:7', conversationId: 'group:7' }
  const api = { getOfflineMessages: async () => [], getRecentMessages: async request => {
    requests.push(request)
    return requests.length === 1
      ? { messages: [received, sent], windowEnd: 200, nextTimestamp: 100, nextId: sent.id, hasMore: true }
      : { messages: [group], windowEnd: 200, nextTimestamp: 100, nextId: group.id, hasMore: false }
  } }
  await loginSync(api, store)()
  assert.equal(privateMessages.length, 2)
  assert.equal(groups.length, 1)
  assert.equal(privateMessages[1].user, 'you')
  assert.equal(privateMessages[1].messageId, sent.id)
  assert.equal(requests[1].afterId, sent.id)
  assert.equal(requests[1].windowEnd, 200)
  assert.equal(groups[0].groupId, '7')
  assert.equal(cursors.length, 0, 'history bootstrap must not advance incremental cursors')
})

test('account switch discards an in-flight history response', async () => {
  let applied = false
  const store = { state: { accountId: '1' }, restoreHistoryPage: () => { applied = true } }
  const api = { getRecentMessages: async () => {
    store.state.accountId = '2'
    return { messages: [{ senderId: '3' }], hasMore: false }
  } }
  await loginSync(api, store)()
  assert.equal(applied, false)
})

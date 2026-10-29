const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const { transpile } = require('./helpers.cjs')
test('manual retry preserves message identity and reply metadata', async () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../src/components/ChatWindow.vue'), 'utf8')
  const start = source.indexOf('async function retryMessage('), end = source.indexOf('// 处理粘贴事件', start)
  const sent = []
  const context = { currentChatId: { value: '42' }, isGroupChat: { value: false }, AGENT_USER_ID: 0,
    MSG_TYPE: { CHAT: 3, GROUP_CHAT: 4, AGENT_CHAT: 5 }, chatTargetId: Number, store: { state: { accountId: 'A' }, updateMessageStatus() {} },
    chatSocket: { sendWrapper: async wrapper => { sent.push(wrapper) }, retryWrapper: async wrapper => { sent.push(wrapper) } },
    toast: { info() {}, error() {} }, t: key => key, normalizeTimeValue: value => Number(value) || 0, console }
  vm.runInNewContext(transpile(source.slice(start, end) + '\nglobalThis.retry = retryMessage'), context)
  const message = { messageId: 'local-stable', text: 'fixture', time: 1, replyTo: { messageId: 'original', content: 'quote', sender: 'fixture' } }
  await context.retry(message)
  assert.equal(sent[0].chat.messageId, 'local-stable')
  assert.equal(sent[0].chat.replyToMessageId, 'original')
  assert.equal(message.messageId, 'local-stable')
})

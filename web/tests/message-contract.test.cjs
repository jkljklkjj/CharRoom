const { readTs, transpile, loadTs } = require('./helpers.cjs')
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const protobuf = require('protobufjs')

const web = path.resolve(__dirname, '..')
const backend = path.resolve(web, '../../chat-room/src/main/proto/message.proto')
const serverRoot = protobuf.loadSync(backend)
const ServerWrapper = serverRoot.lookupType('com.chatlite.proto.MessageWrapper')
const types = serverRoot.lookupEnum('com.chatlite.proto.MessageWrapperType').values
const agentTypes = serverRoot.lookupEnum('com.chatlite.proto.AgentStreamType').values
const wire = object => {
  const bytes = ServerWrapper.encode(ServerWrapper.fromObject(object)).finish()
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
}

test('central protocol constants match every backend enum value', async () => {
  const constants = new vm.SourceTextModule(readTs(path.join(web, 'src/proto/constants.ts')))
  await constants.link(() => { throw new Error('protocol constants must have no runtime dependencies') })
  await constants.evaluate()
  for (const [name, protoName] of Object.entries({ MSG_TYPE: 'MessageWrapperType',
    PROTO_DEVICE_TYPE: 'DeviceType', AGENT_STREAM_TYPE: 'AgentStreamType', CONVERSATION_TYPE: 'ConversationType' })) {
    assert.deepEqual({ ...constants.namespace[name] }, { ...serverRoot.lookupEnum('com.chatlite.proto.' + protoName).values })
    assert.ok(Object.isFrozen(constants.namespace[name]))
  }
})

async function client() {
  const context = vm.createContext({ console, ArrayBuffer, Uint8Array, Blob,
    setTimeout, clearTimeout, setInterval, clearInterval, Date, String, Number })
  const proto = new vm.SyntheticModule(['default'], function () {
    this.setExport('default', { load: () => protobuf.load(path.join(web, 'public/proto/message.proto')) })
  }, { context })
  const shared = new vm.SourceTextModule(readTs(path.join(web, 'src/proto/codec.ts')), { context })
  await shared.link(() => proto)
  await shared.evaluate()
  const codec = new vm.SourceTextModule(readTs(path.join(web, 'src/proto/index.ts')), { context })
  await codec.link(() => shared)
  await codec.evaluate()
  const constantsModule = new vm.SourceTextModule(readTs(path.join(web, 'src/proto/constants.ts')), { context })
  await constantsModule.link(() => { throw new Error('unexpected constants import') })
  await constantsModule.evaluate()
  return { context, codec: codec.namespace, codecModule: codec, constantsModule }
}

test('backend CHAT decodes with numeric enums and exact int64 identifiers', async () => {
  const { codec } = await client()
  const message = await codec.decodeMessage(wire({ type: types.CHAT, chat: {
    userId: '9007199254740993', targetClientId: '42', content: 'contract fixture',
    timestamp: '1792800000000', messageId: 'fixture-private', seqId: '9007199254740995'
  } }))
  assert.equal(message.type, types.CHAT)
  assert.equal(message.chat.userId, '9007199254740993')
  assert.equal(message.chat.seqId, '9007199254740995')
})

test('Web can encode decimal-string IDs for the current backend', async () => {
  const { codec } = await client()
  const message = ServerWrapper.decode(new Uint8Array(await codec.encodeMessage({ type: types.CHAT,
    chat: { targetClientId: '9007199254740993', timestamp: 1792800000000, content: 'fixture' } })))
  assert.equal(message.type, types.CHAT)
  assert.equal(message.chat.targetClientId.toString(), '9007199254740993')
})

function viewHandler(context, store) {
  const view = fs.readFileSync(path.join(web, 'src/views/WebApp.vue'), 'utf8')
  const functionStart = view.indexOf('function handleIncomingMessage(')
  const start = view.lastIndexOf('\n', functionStart) + 1
  const end = view.indexOf('/** 登录后拉取所有会话的离线消息 */', start)
  assert.ok(start >= 0 && end > start)
  Object.assign(context, { store, MSG_TYPE: types, AGENT_STREAM_TYPE: agentTypes,
    normalizeTimestamp: value => new Date(Number(value)).toISOString(),
    api: { getUserDetail: async id => ({ data: { id } }) },
    chatSocket: { sendAck: async () => true } })
  vm.runInContext(transpile(view.slice(start, end) + '\n globalThis.receive = handleIncomingMessage'), context)
  return context.receive
}

test('server private/group messages and Agent chunks reach the view store', async () => {
  const { context } = await client()
  const privateMessages = [], groups = [], chunks = []
  const store = { state: { accountId: '42', users: [{ id: '41' }] },
    addUser() {}, setConversationSeqId() {},
    addMessage: msg => privateMessages.push(msg), addGroupMessage: msg => groups.push(msg),
    upsertAgentStreamMessage: (...args) => chunks.push(args) }
  const receive = viewHandler(context, store)
  const fixtures = [
    { type: types.CHAT, chat: { userId: '41', targetClientId: '42', content: 'private', messageId: 'private' } },
    { type: types.GROUP_CHAT, groupChat: { userId: '41', targetClientId: '7', content: 'group', messageId: 'group' } },
    { type: types.AGENT_CHAT_STREAM, agentStream: { requestId: '123', type: 0, text: 'chunk' } }
  ]
  for (const fixture of fixtures) {
    const message = ServerWrapper.toObject(ServerWrapper.decode(new Uint8Array(wire(fixture))), { longs: String, enums: Number, defaults: true })
    await receive(message)
  }
  assert.equal(privateMessages.length, 1)
  assert.equal(privateMessages[0].text, 'private')
  assert.equal(groups.length, 1)
  assert.equal(chunks.length, 1)
})

test('Worker uses the same numeric enum and int64 contract', async () => {
  const sent = []
  const root = await protobuf.load(path.join(web, 'public/proto/message.proto'))
  const self = { postMessage: msg => sent.push(msg) }
  await loadTs(path.join(web, 'src/proto/worker.ts'), { self }, {
    protobufjs: { default: { load: async () => root } }
  })
  await self.onmessage({ data: { type: 'init', id: -1 } })
  await self.onmessage({ data: { type: 'decode', id: 1,
    payload: wire({ type: types.ACK, ack: { success: true, messageId: 'fixture', seqId: '9007199254740993' } }) } })
  const response = sent.find(item => item.id === 1)
  assert.equal(response?.type, 'decode', response?.error)
  assert.equal(response.result.type, types.ACK)
  assert.equal(response.result.ack.seqId, '9007199254740993')
})

test('real decoded CHAT reaches the UI and only a successful server ACK marks sent', async () => {
  const { context, codecModule, constantsModule } = await client()
  context.document = { visibilityState: 'visible' }
  const statuses = [], received = []
  const store = { state: { accountId: '42', users: [{ id: '41' }] },
    addMessage: msg => received.push(msg), setConversationSeqId() {},
    updateMessageStatus: (...args) => statuses.push(args) }
  const receive = viewHandler(context, store)
  let transport
  const exportsModule = entries => new vm.SyntheticModule(Object.keys(entries), function () {
    for (const [name, value] of Object.entries(entries)) this.setExport(name, value)
  }, { context })
  const dependencies = {
    '../proto': codecModule,
    '../proto/constants': constantsModule,
    'dompurify': exportsModule({ default: { sanitize: text => text } }),
    '../i18n': exportsModule({ default: { global: { t: key => key } } }),
    './transport/TransportFactory': exportsModule({
      isWebTransportSupported: () => true, buildWebTransportUrl: () => 'https://fixture.invalid',
      createTransport: () => (transport = {
        async connect() { this.connected = true; this.onopen() },
        isConnected() { return this.connected }, send() { return true },
        close() { this.connected = false }
      })
    }),
    './offlineQueue': exportsModule({ saveToQueue: async () => {}, getAllPending: async () => [],
      removeMessage: async () => {}, incrementRetry: async () => {}, MAX_RETRIES: 3 }),
    '../utils/device': exportsModule({ getDeviceId: () => 'fixture', getDeviceType: () => 'web' })
  }
  const socketModule = new vm.SourceTextModule(readTs(path.join(web, 'src/services/chatSocket.ts')), { context })
  await socketModule.link(name => dependencies[name])
  await socketModule.evaluate()
  const socket = socketModule.namespace
  socket.setStore(store)
  const until = async predicate => {
    const deadline = Date.now() + 1000
    while (!predicate() && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 5))
    assert.ok(predicate(), 'message processing did not finish')
  }
  try {
    await socket.connect('fixture.invalid', 443, 'fixture-token', '42', { onmessage: receive })
    transport.onmessage(wire({ type: types.CHAT,
      chat: { userId: '41', targetClientId: '42', content: 'received fixture', messageId: 'incoming' } }))
    await until(() => received.length === 1)
    assert.equal(received.length, 1)
    for (const [messageId, success] of [['accepted', true], ['rejected', false]]) {
      await socket.sendWrapper({ type: types.CHAT, chat: { targetClientId: '41', content: 'fixture', messageId } })
      transport.onmessage(wire({ type: types.ACK, ack: { messageId, success } }))
      await until(() => statuses.some(([id]) => id === messageId))
    }
    assert.ok(statuses.some(([id, status]) => id === 'accepted' && status === 'sent'))
    assert.ok(statuses.some(([id, status]) => id === 'rejected' && status === 'failed'))
    assert.ok(!statuses.some(([id, status]) => id === 'rejected' && status === 'sent'))
  } finally { socket.close() }
})

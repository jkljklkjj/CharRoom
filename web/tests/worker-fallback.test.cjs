const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const protobuf = require('protobufjs')
const { loadTs } = require('./helpers.cjs')
const root = protobuf.loadSync(path.resolve(__dirname, '../src/proto/message.proto'))
const wrapper = root.lookupType('com.chatlite.proto.MessageWrapper')
test('healthy Worker encodes without evaluating the main-thread codec dependency', async () => {
  const forbidden = Object.defineProperty({}, 'default', { enumerable:true, get() { throw new Error('main-thread codec was eagerly loaded') } })
  class Worker {
    postMessage(request) { queueMicrotask(() => this.onmessage({ data: request.type === 'init' ? { type:'init', id:0 } : { type:'encode', id:request.id, result:new ArrayBuffer(1) } })) }
    terminate() {}
  }
  const { exports: codec } = await loadTs(path.resolve(__dirname, '../src/proto/index.ts'), { Worker }, { protobufjs:forbidden })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal((await codec.encodeMessage({ type:3, chat:{ content:'fixture' } })).byteLength, 1)
})
for (const failure of ['reply-error', 'crash', 'timeout']) {
  test(`Worker ${failure} falls back with the original transferable input intact`, async () => {
    class Worker {
      constructor() { this.terminated = false }
      postMessage(request, transfer = []) {
        if (request.type === 'init') { queueMicrotask(() => this.onmessage({ data: { type: 'init', id: 0 } })); return }
        // Simulate the actual browser transfer, which detaches the posted buffer.
        structuredClone(request, { transfer })
        if (failure === 'reply-error') queueMicrotask(() => this.onmessage({ data: { type: 'error', id: request.id, error: 'fixture error' } }))
        if (failure === 'crash') queueMicrotask(() => this.onerror({ message: 'fixture crash' }))
      }
      terminate() { this.terminated = true }
    }
    const { exports: codec } = await loadTs(path.resolve(__dirname, '../src/proto/index.ts'), {
      Worker, setTimeout: (fn, ms) => setTimeout(fn, Math.min(ms, 20))
    }, { protobufjs: { default: { load: async () => root } } })
    await new Promise(resolve => setImmediate(resolve))
    assert.equal(codec.getWorkerStatus().ready, true)
    const input = Uint8Array.from(wrapper.encode(wrapper.fromObject({ type: 3, chat: { content: 'fallback', userId: '9007199254740993' } })).finish()).buffer
    const message = await codec.decodeMessage(input)
    assert.equal(message.chat.content, 'fallback')
    assert.equal(message.chat.userId, '9007199254740993')
    assert.ok(input.byteLength > 0)
    assert.equal(codec.getWorkerStatus().failed, true)
  })
}

const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')

test('sync drains every page, coalesces hints and commits only after durable apply', async () => {
  const { exports: { ConversationSync } } = await loadTs(path.resolve(__dirname, '../src/services/conversationSync.ts'))
  let cursor = '0', release
  const requests = [], commits = [], applied = []
  const gate = new Promise(resolve => { release = resolve })
  const sync = new ConversationSync({
    capture: () => ({ key: 'A:1', isCurrent: () => true }),
    cursor: () => cursor,
    fetch: async (_, after) => {
      requests.push(after)
      return { messages: [{ seqId: after === '0' ? '1' : '2' }], nextSeqId: after === '0' ? '1' : '2', hasMore: after === '0' }
    },
    apply: async (_, rows) => { await gate; applied.push(rows[0].seqId) },
    commit: (_, value) => { cursor = value; commits.push(value) }
  })
  const first = sync.run('user:1:2', '1')
  const second = sync.run('user:1:2', '2')
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(commits, [])
  release()
  await Promise.all([first, second])
  assert.deepEqual(requests, ['0', '1'])
  assert.deepEqual(applied, ['1', '2'])
  assert.deepEqual(commits, ['1', '2'])
})

test('failed persistence and a switched session never advance a cursor', async () => {
  const { exports: { ConversationSync } } = await loadTs(path.resolve(__dirname, '../src/services/conversationSync.ts'))
  let current = true, commits = 0, fail = true
  const sync = new ConversationSync({
    capture: () => ({ key: 'A:1', isCurrent: () => current }), cursor: () => '0',
    fetch: async () => ({ messages: [{ seqId: '9007199254740993' }], nextSeqId: '9007199254740993', hasMore: false }),
    apply: async () => { if (fail) throw new Error('disk full'); current = false },
    commit: () => { commits++ }
  })
  await assert.rejects(sync.run('x', '9007199254740993'), /disk full/)
  fail = false
  await sync.run('x', '9007199254740993')
  assert.equal(commits, 0)
})

test('nonadvancing pages fail instead of looping or skipping rows', async () => {
  const { exports: { ConversationSync } } = await loadTs(path.resolve(__dirname, '../src/services/conversationSync.ts'))
  const sync = new ConversationSync({ capture: () => ({ key: 'A:1', isCurrent: () => true }), cursor: () => '0',
    fetch: async () => ({ messages: [{ seqId: '1' }], nextSeqId: '0', hasMore: true }),
    apply: async () => assert.fail('invalid cursor must be rejected before persistence'), commit: () => assert.fail() })
  await assert.rejects(sync.run('x', '1'), /cursor/i)
})

test('unsafe numeric sequences are rejected rather than rounded', async () => {
  const { exports: { ConversationSync } } = await loadTs(path.resolve(__dirname, '../src/services/conversationSync.ts'))
  const sync = new ConversationSync({ capture: () => ({ key: 'A', isCurrent: () => true }), cursor: () => '0',
    fetch: async () => assert.fail(), apply: async () => assert.fail(), commit: () => assert.fail() })
  await assert.rejects(sync.run('x', Number('9007199254740993')), /exact decimal string/)
})

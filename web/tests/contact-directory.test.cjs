const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const file = path.resolve(__dirname, '../src/utils/contactDirectory.ts')

test('directory excludes self/assistant, merges groups without ID collisions and sorts by name', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 0 }, { id: '7' }, { id: 21, username: 'Zoe' }, { id: 22, username: 'Alice' }, { id: 21, username: 'Old group name', conversationKind:'group' }]
  const merged = api.sidebarUsers(users, [{ id: '21', name: 'Walking' }])
  const directory = api.contactDirectory(merged, 7, 'en')
  assert.deepEqual(Array.from(directory.friends, u => u.id), [22, 21])
  assert.deepEqual(Array.from(directory.groups, u => u.id), ['21'])
  assert.equal(directory.groups[0].username, 'Walking')
  assert.deepEqual(users.map(u => u.id), [0, '7', 21, 22, 21])
  assert.equal(api.contactDirectory(users.slice(0, 2), 7, 'en').friends.length, 0)
})

test('search trims input, matches names/accounts and group target stays positive on wire', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 21, username: 'Alice', account: 'flower' }, { id: 21, username: 'Walking', conversationKind:'group' }]
  assert.equal(api.filterContactDirectory(api.contactDirectory(users, 7, 'en'), ' ALI ').friends.length, 1)
  assert.equal(api.filterContactDirectory(api.contactDirectory(users, 7, 'en'), 'flower').friends.length, 1)
  assert.equal(api.filterContactDirectory(api.contactDirectory(users, 7, 'en'), '21').groups.length, 1)
  assert.equal(api.chatTargetId('group:21'), '21')
  assert.equal(api.chatTargetId('user:21'), '21')
  assert.equal(api.chatTargetId('user:0'), '0')
})

test('search is substring-based and normalizes width/case instead of requiring an exact match', async () => {
  const { exports: api } = await loadTs(file)
  const users = [
    { id: 12, username: '张三2号' },
    { id: 33, username: 'Wang' },
    { id: 44, username: 'x', name: '昵称２' }
  ]
  // 子串即可命中，不需要完整昵称；半角 "2" 也能命中全角「昵称２」
  assert.deepEqual(Array.from(api.filterContacts(users, '2').map(u => u.id)), [12, 44])
  assert.deepEqual(Array.from(api.filterContacts(users, '三2').map(u => u.id)), [12])
  // 全角数字 / 全角空格 / 大小写归一
  assert.deepEqual(Array.from(api.filterContacts(users, '２').map(u => u.id)), [12, 44])
  assert.deepEqual(Array.from(api.filterContacts(users, 'ｗａｎｇ').map(u => u.id)), [33])
  assert.deepEqual(Array.from(api.filterContacts(users, '  WANG ').map(u => u.id)), [33])
})

test('filtering a cached directory never repeats locale sorting', async () => {
  let comparisons = 0
  const collator = new Intl.Collator('en', { sensitivity: 'base', numeric: true })
  const { exports: api } = await loadTs(file, { Intl: { Collator: class {
    compare(a, b) { comparisons++; return collator.compare(a, b) }
  } } })
  const users = Array.from({ length: 200 }, (_, i) => ({ id: i + 10, username: `Contact ${200-i}` }))
  const directory = api.contactDirectory(users, 7, 'en')
  const before = comparisons
  assert.ok(before > 0)
  for (const query of ['C', 'Contact 2', ' Contact 20 ']) {
    const filtered = api.filterContactDirectory(directory, query)
    assert.ok(filtered.friends.length > 0)
  }
  assert.equal(comparisons, before)
  assert.equal(api.filterContactDirectory(directory, '  '), directory)
})

test('conversation ranking reads activity once per contact and preserves ties and mixed IDs', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 21 }, { id: 21, conversationKind: 'group' }, { id: 22 }]
  const calls = []
  const sorted = api.sortConversationUsers(users, key => {
    calls.push(key)
    return key === 'group:21' ? { lastIncomingMessageTime: 10, unreadCount: 1 } : undefined
  })
  assert.deepEqual(Array.from(calls), ['user:21', 'group:21', 'user:22'])
  assert.deepEqual(Array.from(sorted), [users[1], users[0], users[2]])
  assert.deepEqual(users.map(u => u.id), [21, 21, 22])
})

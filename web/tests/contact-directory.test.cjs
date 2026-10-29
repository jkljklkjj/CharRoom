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

test('search trims input and only matches nicknames, never the account/ID', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 21, username: 'Alice', account: 'flower' }, { id: 21, username: 'Walking', conversationKind:'group' }]
  const directory = api.contactDirectory(users, 7, 'en')
  assert.equal(api.filterContactDirectory(directory, ' ALI ').friends.length, 1)
  assert.equal(api.filterContactDirectory(directory, 'walk').groups.length, 1)
  // 账号和数字 ID 都不再作为联系人的模糊关键字
  assert.equal(api.filterContactDirectory(directory, 'flower').friends.length, 0)
  assert.equal(api.filterContactDirectory(directory, '21').friends.length, 0)
  assert.equal(api.filterContactDirectory(directory, '21').groups.length, 0)
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

test('sidebar hides the signed-in account but keeps a same-numbered group', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 7, username: 'Me' }, { id: 21, username: 'Other' }]
  const merged = api.sidebarUsers(users, [{ id: '7', name: 'Room 7' }], 7)
  assert.deepEqual(Array.from(merged.map(u => `${u.conversationKind || 'private'}:${u.id}`)), ['private:21', 'group:7'])
  // 不传 selfId 时保持旧行为，调用方必须显式声明“我是谁”
  assert.equal(api.sidebarUsers(users, []).length, 2)
})

test('pinned conversations lead regardless of activity and unread count', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 21 }, { id: 22 }, { id: 23 }]
  const sorted = api.sortConversationUsers(users, key => {
    if (key === 'user:21') return { lastIncomingMessageTime: 100, unreadCount: 0 }
    if (key === 'user:22') return { lastIncomingMessageTime: 50, unreadCount: 3 }
    return undefined
  }, key => key === 'user:23')
  assert.deepEqual(Array.from(sorted, u => u.id), [23, 21, 22])
})

const test = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const file = path.resolve(__dirname, '../src/utils/contactDirectory.ts')

test('directory excludes self/assistant, merges groups without ID collisions and sorts by name', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 0 }, { id: '7' }, { id: 21, username: 'Zoe' }, { id: 22, username: 'Alice' }, { id: -21, username: 'Old group name' }]
  const merged = api.sidebarUsers(users, [{ id: '21', name: 'Walking' }])
  const directory = api.contactDirectory(merged, 7, '', 'en')
  assert.deepEqual(Array.from(directory.friends, u => u.id), [22, 21])
  assert.deepEqual(Array.from(directory.groups, u => u.id), [-21])
  assert.equal(directory.groups[0].username, 'Walking')
  assert.deepEqual(users.map(u => u.id), [0, '7', 21, 22, -21])
  assert.equal(api.contactDirectory(users.slice(0, 2), 7, '', 'en').friends.length, 0)
})

test('search trims input, matches names/accounts and group target stays positive on wire', async () => {
  const { exports: api } = await loadTs(file)
  const users = [{ id: 21, username: 'Alice', account: 'flower' }, { id: -21, username: 'Walking' }]
  assert.equal(api.contactDirectory(users, 7, ' ALI ', 'en').friends.length, 1)
  assert.equal(api.contactDirectory(users, 7, 'flower', 'en').friends.length, 1)
  assert.equal(api.contactDirectory(users, 7, '21', 'en').groups.length, 1)
  assert.equal(api.chatTargetId('-21'), '21')
  assert.equal(api.chatTargetId('21'), '21')
  assert.equal(api.chatTargetId(0), '0')
})

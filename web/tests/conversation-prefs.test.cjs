const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const file = path.resolve(__dirname, '../src/store/conversationPrefs.ts')

function fixture(accountId = 'A') {
  const values = new Map()
  const localStorage = {
    getItem: key => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  }
  const state = { accountId, pinnedConversations: [], hiddenConversations: [] }
  const load = () => loadTs(file, { localStorage }, {
    './state': { state },
    './storage': { getConversationKey: id => /^(user|group):(0|[1-9][0-9]*)$/.test(String(id)) ? String(id) : '' }
  })
  return { values, state, load }
}

test('pin and hidden preferences persist per account and survive a reload', async () => {
  const { values, state, load } = fixture('A')
  const { exports: prefs } = await load()
  prefs.toggleConversationPin('user:21')
  prefs.toggleConversationPin('group:7')
  prefs.hideConversation('group:7')
  assert.deepEqual([...state.pinnedConversations], ['user:21'])
  assert.deepEqual([...state.hiddenConversations], ['group:7'])
  assert.ok([...values.keys()].some(key => key.includes('_A_')), 'preferences belong to the account')

  state.accountId = 'B'
  prefs.loadConversationPrefs('B')
  assert.deepEqual([...state.pinnedConversations], [])
  assert.deepEqual([...state.hiddenConversations], [])

  state.accountId = 'A'
  prefs.loadConversationPrefs('A')
  assert.deepEqual([...state.pinnedConversations], ['user:21'])
  assert.deepEqual([...state.hiddenConversations], ['group:7'])
})

test('deleting only hides the row, pinning it again reveals it, and new messages reveal it', async () => {
  const { state, load } = fixture('A')
  const { exports: prefs } = await load()
  prefs.hideConversation('user:21')
  assert.equal(prefs.isConversationHidden('user:21'), true)
  assert.equal(prefs.isConversationPinned('user:21'), false)

  prefs.toggleConversationPin('user:21')
  assert.equal(prefs.isConversationPinned('user:21'), true)
  assert.equal(prefs.isConversationHidden('user:21'), false)

  prefs.hideConversation('user:21')
  assert.equal(prefs.isConversationHidden('user:21'), true)
  assert.equal(prefs.isConversationPinned('user:21'), false)

  prefs.revealConversation('user:21')
  assert.equal(prefs.isConversationHidden('user:21'), false)
  assert.equal(prefs.isConversationPinned('user:21'), false)
})

test('corrupt or foreign keys never hide a real conversation', async () => {
  const { values, state, load } = fixture('A')
  values.set('charroom_chat_history_A_conversation_prefs', JSON.stringify({
    version: 1, pinned: ['user:21', 'bogus'], hidden: ['group:7', 'user:21', 42]
  }))
  const { exports: prefs } = await load()
  prefs.loadConversationPrefs('A')
  assert.deepEqual([...state.pinnedConversations], ['user:21'])
  assert.deepEqual([...state.hiddenConversations], ['group:7'])

  values.set('charroom_chat_history_A_conversation_prefs', '{not json')
  prefs.loadConversationPrefs('A')
  assert.deepEqual([...state.pinnedConversations], [])
  assert.deepEqual([...state.hiddenConversations], [])
})

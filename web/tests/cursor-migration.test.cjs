const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
test('unverified legacy cursors reset once; only versioned applied cursors restore', async () => {
  const state = { accountId: '', users: [], groups: [] }
  let raw = JSON.stringify({ 'user:1:2': '9007199254740993' })
  const { exports: auth } = await loadTs(path.resolve(__dirname, '../src/store/auth.ts'), {
    localStorage: { getItem: () => raw }
  }, { './state': { state }, './storage': { buildAccountPrefix: id => String(id) },
    './users': { rebuildConversationStates() {} }, '../services/sessionEpoch': { sessionEpoch: { renew() {} } } })
  auth.setAccountId('1')
  assert.deepEqual({ ...state.conversationSeqIds }, {})
  raw = JSON.stringify({ version: 2, cursors: { good: '9007199254740993', invalid: -1, unsafe: Number('9007199254740993') } })
  auth.setAccountId('1')
  assert.deepEqual({ ...state.conversationSeqIds }, { good: '9007199254740993' })
})

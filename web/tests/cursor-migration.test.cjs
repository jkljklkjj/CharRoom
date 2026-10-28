const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
test('localStorage cursors are never accepted as committed IndexedDB cursors', async () => {
  const state = { accountId: '', users: [], groups: [] }
  let raw = JSON.stringify({ 'user:1:2': '9007199254740993' })
  const { exports: auth } = await loadTs(path.resolve(__dirname, '../src/store/auth.ts'), {
    localStorage: { getItem: () => raw }
  }, { './state': { state }, './storage': { buildAccountPrefix: id => String(id) },
    './messages': { resetConversationViews() {} }, './users': { rebuildConversationStates() {},clearConversationPreviewCache() {} }, '../services/sessionEpoch': { sessionEpoch: { renew() {} } } })
  auth.setAccountId('1')
  assert.deepEqual({ ...state.conversationSeqIds }, {})
  raw = JSON.stringify({ version: 2, cursors: { good: '9007199254740993', invalid: -1, unsafe: Number('9007199254740993') } })
  auth.setAccountId('1')
  assert.deepEqual({ ...state.conversationSeqIds }, {})
})

test('logout clears only the captured owner social snapshots/drafts and resets session memory',async()=>{
  const values=new Map([
    ['charroom_social_profile_A','owner profile'],['charroom_daily_answer_A','owner draft'],
    ['charroom_social_profile_B','other profile'],['charroom_daily_answer_B','other draft']
  ])
  let clears=0
  const state={accountId:'A'}
  const {exports:auth}=await loadTs(path.resolve(__dirname,'../src/store/auth.ts'),{
    localStorage:{getItem:key=>values.get(key),removeItem:key=>values.delete(key)}
  },{'./state':{state},'./storage':{buildAccountPrefix:id=>`history_${id}_`},
    './messages':{resetConversationViews(){}},'./users':{rebuildConversationStates(){},clearConversationPreviewCache(){}},
    '../services/sessionEpoch':{sessionEpoch:{renew(){}}},'../services/socialViewCache':{socialViewCache:{clear(){clears++}}}
  })
  auth.clearAll()
  assert.equal(values.has('charroom_social_profile_A'),false);assert.equal(values.has('charroom_daily_answer_A'),false)
  assert.equal(values.has('charroom_social_profile_B'),true);assert.equal(values.has('charroom_daily_answer_B'),true)
  assert.equal(clears,1)
})

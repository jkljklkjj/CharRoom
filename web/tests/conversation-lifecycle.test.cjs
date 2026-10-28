const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
async function fixture(read, cursors = async()=>({})) {
  const state = { accountId: 'A', selectedChatId: null, messages: [], groupMessages: [], conversationSeqIds: {} }
  let version = 0
  const { exports: messages } = await loadTs(path.resolve(__dirname, '../src/store/messages.ts'), {}, {
    './state': { state, PAGE_SIZE: 50 }, './storage': { getPrivateConversationId: m => m.targetId, normalizeTimeValue: Number },
    './users': { updateConversationState() {}, invalidateConversationPreview() {} },
    '../utils/format': { conversationPreviewText: m => m.text }, dompurify: { default: { sanitize: t => t } },
    '../services/sessionEpoch': { sessionEpoch: { capture: () => { const captured = version; return { version, isCurrent: () => captured === version } } } },
    '../utils/messageDB': { getAppliedCursors:cursors,getMessageCursor:async()=>null, getMessagesPage: read, appendMessage: async () => {}, mergeHistoryMessages: async () => {}, deleteMessage() {} }
  })
  return { state, messages, renew: () => { version++ } }
}
const page = id => ({ messages: [{ messageId: id, text: id, time:1 }], beforeIndex: [1, id, id], hasMore: true })

test('committed history batches merge without rereading the loaded window',async()=>{
 let reads=0
 const {messages,state}=await fixture(async()=>{reads++;return page('initial')})
 await messages.loadConversation('user:42')
 for(let i=0;i<20;i++)await messages.restoreHistoryPage('A',[{messageId:`sync-${i}`,targetId:'42',user:'you',text:'synced',time:i+2}])
 assert.equal(reads,1);assert.equal(state.messages.length,21)
 assert.equal(state.messages.at(-1).messageId,'sync-19')
})

test('active display window remains bounded and can return to durable latest rows',async()=>{
 const {messages,state}=await fixture(async()=>page('latest'))
 await messages.loadConversation('user:42')
 state.historyFollowingLatest=false
 const rows=Array.from({length:3000},(_,i)=>({messageId:`sync-${i}`,targetId:'42',user:'you',text:'body',time:i+2}))
 await messages.restoreHistoryPage('A',rows)
 assert.equal(state.messages.length,2000);assert.equal(state.messages[0].messageId,'latest')
 assert.equal(state.historyHasNewer,true)
 await messages.loadLatestMessages()
 assert.equal(state.messages.length,1);assert.equal(state.historyHasNewer,false)
})

test('a same-number private peer and group have isolated visible histories and cached buckets',async()=>{
 const {messages,state}=await fixture(async(_,id)=>({messages:[],beforeIndex:null,hasMore:false}))
 await messages.loadConversation('user:42')
 await messages.addGroupMessage({messageId:'group-live',groupId:'42',user:'43',text:'group',time:1})
 assert.equal(state.messages.length,0);assert.equal(state.groupMessages.length,0)
 await messages.addMessage({messageId:'private-live',targetId:'42',user:'you',text:'private',time:2})
 assert.equal(state.messages[0].messageId,'private-live')
 await messages.loadConversation('group:42')
 await messages.addMessage({messageId:'private-hidden',targetId:'42',user:'you',text:'private hidden',time:3})
 assert.equal(state.groupMessages.length,0)
 await messages.loadConversation('user:42')
 assert.deepEqual(Array.from(state.messages,row=>row.messageId),['private-live','private-hidden'])
})

test('late conversation A cannot overwrite B, including A→B→A', async () => {
  const first = deferred(); let calls = 0
  const { messages, state } = await fixture(async (_, id) => ++calls === 1 ? first.promise : page(id))
  const pending = messages.loadConversation('user:1')
  await messages.loadConversation('user:2')
  await messages.loadConversation('user:1')
  first.resolve(page('stale'))
  await pending
  assert.equal(state.messages[0].messageId, '1')
})

test('older-page requests coalesce and cannot prepend to another account', async () => {
  const older = deferred(); let calls = 0
  const { messages, state, renew } = await fixture(async () => ++calls === 1 ? page('new') : older.promise)
  await messages.loadConversation('user:1')
  const first = messages.loadOlderMessages(), second = messages.loadOlderMessages()
  assert.equal(first, second)
  renew(); state.accountId = 'B'; state.messages = []
  older.resolve(page('old-A'))
  assert.equal(await first, false)
  assert.equal(state.messages.length, 0)
  assert.equal(calls, 2)
})

test('returning to a cached conversation renders its loaded page synchronously without clearing it', async () => {
  const delayed = deferred(); let calls = 0
  const { messages, state } = await fixture(async (_, id) => ++calls <= 2 ? page(id) : delayed.promise)
  await messages.loadConversation('user:1')
  await messages.loadConversation('user:2')
  const pending = messages.loadConversation('user:1')
  assert.equal(state.messages[0]?.messageId, '1')
  delayed.resolve(page('1'))
  await pending
  assert.equal(state.messages[0]?.messageId, '1')
})

test('a local page completing after a live arrival preserves the new message', async () => {
  const delayed = deferred()
  const { messages, state } = await fixture(() => delayed.promise)
  const pending = messages.loadConversation('user:1')
  await messages.addMessage({messageId:'live',targetId:'1',user:'you',text:'new',time:2})
  delayed.resolve(page('older'))
  await pending
  assert.deepEqual(Array.from(state.messages, row => row.messageId), ['older','live'])
})

test('a cached hidden conversation receives live deltas without another local full read',async()=>{
  let calls=0
  const {messages,state}=await fixture(async(_,id)=>{calls++;return page(id)})
  await messages.loadConversation('user:1');await messages.loadConversation('user:2')
  await messages.addMessage({messageId:'live',targetId:'1',user:'you',text:'fixture',time:2})
  await messages.loadConversation('user:1')
  assert.deepEqual(Array.from(state.messages,row=>row.messageId),['1','live'])
  assert.equal(calls,2)
})

test('a new session cannot cache the previous account visible rows under its own keys',async()=>{
  const {messages,state,renew}=await fixture(async(account)=>page(account))
  await messages.loadConversation('user:1');renew();state.accountId='B'
  await messages.loadConversation('user:2');await messages.loadConversation('user:1')
  assert.deepEqual(Array.from(state.messages,row=>row.messageId),['B'])
})

test('manual retry restarts the indicator clock and cached views retain it across automatic retries',async()=>{
  const {messages,state}=await fixture(async(_,id)=>page(id))
  await messages.loadConversation('user:1')
  messages.updateMessageStatus('1','sending',100000)
  messages.updateMessageStatus('1','sending')
  assert.equal(state.messages[0].sendStartedAt,100000)
  await messages.loadConversation('user:2');await messages.loadConversation('user:1')
  assert.equal(state.messages[0].sendStartedAt,100000)
  messages.confirmMessageId('1','1001','1')
  assert.equal(state.messages[0].isSent,'sent')
})

test('history sync status counts overlapping jobs and ignores completions from an old session',async()=>{
  const {messages,state,renew}=await fixture(async()=>page('1'))
  const a=messages.beginHistorySync(),b=messages.beginHistorySync()
  a(true);assert.equal(state.historySyncing,true);assert.equal(state.historySyncError,true)
  b();assert.equal(state.historySyncing,false)
  const old=messages.beginHistorySync();renew();const current=messages.beginHistorySync()
  old();assert.equal(state.historySyncing,true)
  current();assert.equal(state.historySyncing,false);assert.equal(state.historySyncError,false)
})

test('an authenticated session restores exact committed cursors and ignores late reads after logout',async()=>{
  const f=await fixture(async()=>page('1'),async()=>({'A:1':'9007199254740999'}))
  await f.messages.restoreAppliedCursors()
  assert.equal(f.state.conversationSeqIds['A:1'],'9007199254740999')
  const delayed=deferred(),other=await fixture(async()=>page('1'),()=>delayed.promise)
  const pending=other.messages.restoreAppliedCursors()
  other.renew();other.state.accountId='B';delayed.resolve({'A:1':'9007199254740999'})
  await pending;assert.deepEqual({...other.state.conversationSeqIds},{})
})

const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {loadTs}=require('./helpers.cjs')
const file=path.resolve(__dirname,'../src/utils/messageSendState.ts')

test('pending messages are quiet before 4s, spinning until 10s, and failed afterward',async()=>{
  const {exports:{messageSendState}}=await loadTs(file)
  const start=100000
  assert.equal(messageSendState('sending',start,start).phase,null)
  assert.equal(messageSendState('sending',start,start+3999).phase,null)
  assert.equal(messageSendState('sending',start,start+4000).phase,'sending')
  assert.equal(messageSendState('sending',start,start+9999).phase,'sending')
  assert.equal(messageSendState('sending',start,start+10000).phase,'failed')
  assert.equal(messageSendState('sending',start,start+60000).nextAt,null)
})

test('success including a late ACK clears the indicator and all scheduled transitions',async()=>{
  const {exports:{messageSendState}}=await loadTs(file)
  for(const now of [100001,105000,112000]){
    const state=messageSendState('sent',100000,now)
    assert.equal(state.phase,null);assert.equal(state.nextAt,null)
  }
  assert.equal(messageSendState(undefined,100000,120000).phase,null)
})

test('only user retry restarts the quiet period; definitive errors stay failed',async()=>{
  const {exports:{messageSendState}}=await loadTs(file)
  assert.equal(messageSendState('failed',100000,100001).phase,'failed')
  assert.equal(messageSendState('sending',100000,112000).phase,'failed')
  assert.equal(messageSendState('sending',112000,112001).phase,null)
  assert.equal(messageSendState('sending',112000,112001).nextAt,116000)
  assert.equal(messageSendState('sending',112000,116000).nextAt,122000)
})

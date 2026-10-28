const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {loadTs}=require('./helpers.cjs')
test('idle encounter polling slows down and failures back off to a bounded delay',async()=>{
 const {exports:{encounterPollDelay}}=await loadTs(path.resolve(__dirname,'../src/utils/businessPolling.ts'))
 assert.equal(encounterPollDelay('ACTIVE',0),1200);assert.equal(encounterPollDelay('IDLE',0),15000)
 assert.equal(encounterPollDelay('ACTIVE',1),4000);assert.equal(encounterPollDelay('ACTIVE',100),60000)
})

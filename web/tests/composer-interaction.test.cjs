const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {loadTs}=require('./helpers.cjs')
const source=path.resolve(__dirname,'../src/utils/composer.ts')
test('IME candidate confirmation preserves normal editing and does not send',async()=>{
  const {exports:{composerEnter}}=await loadTs(source)
  let sent=0,prevented=0
  for(const event of [{isComposing:true},{keyCode:229}])composerEnter({key:'Enter',preventDefault(){prevented++},...event},()=>sent++)
  assert.equal(sent,0);assert.equal(prevented,0)
})
test('modified Enter preserves line breaks and platform editing shortcuts',async()=>{
  const {exports:{composerEnter}}=await loadTs(source)
  let sent=0
  for(const key of ['shiftKey','ctrlKey','altKey','metaKey'])composerEnter({key:'Enter',[key]:true,preventDefault(){assert.fail('editing shortcut prevented')}},()=>sent++)
  assert.equal(sent,0)
})
test('plain Enter submits once and consumes the newline',async()=>{
  const {exports:{composerEnter}}=await loadTs(source)
  let sent=0,prevented=0
  composerEnter({key:'Enter',preventDefault(){prevented++}},()=>sent++)
  assert.equal(sent,1);assert.equal(prevented,1)
})

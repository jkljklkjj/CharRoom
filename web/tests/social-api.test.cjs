const {test}=require('node:test')
const assert=require('node:assert/strict')
const path=require('node:path')
const {loadTs}=require('./helpers.cjs')
const source=path.resolve(__dirname,'../src/api/social.ts')
test('social API rejects business errors even when HTTP succeeds',async()=>{
  const {exports:api}=await loadTs(source,{}, {'./http':{API_BASE:'/api',safeFetch:async()=>({ok:true,status:200,body:{code:1005,message:'restricted'}})}})
  await assert.rejects(api.getSocialProfile('42'),/restricted/)
})
test('encounter retries preserve the caller message ID and expected room',async()=>{
  const calls=[]
  const {exports:api}=await loadTs(source,{}, {'./http':{API_BASE:'/api',safeFetch:async(url,options)=>{calls.push({url,...options});return {ok:true,status:200,body:{code:0,data:{state:'ACTIVE',messages:[]}}}}}})
  const message={action:'send',roomId:'room-A',messageId:'stable-id-123',content:'hello'}
  await api.actEncounter(message);await api.actEncounter(message)
  assert.deepEqual(calls.map(c=>JSON.parse(c.body).messageId),['stable-id-123','stable-id-123'])
  assert.ok(calls.every(c=>JSON.parse(c.body).roomId==='room-A'))
})
test('discovery search parameters are escaped and cancellation is forwarded',async()=>{
  let call
  const {exports:api}=await loadTs(source,{URLSearchParams}, {'./http':{API_BASE:'/api',safeFetch:async(url,options)=>{call={url,options};return {ok:true,status:200,body:{code:0,data:{users:[]}}}}}})
  const signal=new AbortController().signal
  await api.discoverPeople({q:'猫&狗',channel:'all',cursor:0},signal)
  assert.equal(new URL('http://local'+call.url).searchParams.get('q'),'猫&狗')
  assert.equal(call.options.signal,signal)
})

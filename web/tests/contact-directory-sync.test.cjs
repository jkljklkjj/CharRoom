const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const {loadTs} = require('./helpers.cjs')
const defer = () => {let resolve; const promise = new Promise(r => resolve=r); return {promise,resolve}}
const snapshot = (version, ids) => ({version,users:ids.map(id=>({id,username:id})),groups:[]})
async function fixture(load, fetch) {
 let epoch=0, mutation=0; const shown=[],saved=[],status=[]
 const {exports:mod}=await loadTs(path.resolve(__dirname,'../src/services/contactDirectorySync.ts'))
 const sync=new mod.ContactDirectorySync({capture:()=>{const v=epoch;return {key:String(v),accountId:'A',isCurrent:()=>v===epoch}},
  load,fetch,save:async (account,value)=>saved.push([account,value]),apply:value=>shown.push(value),status:value=>status.push(value),now:()=>100})
 return {sync,shown,saved,status,renew:()=>epoch++}
}
test('local directory is displayed before validation and concurrent refreshes share one request',async()=>{
 const network=defer();let calls=0
 const f=await fixture(async()=>snapshot('v1',['1']),async version=>{calls++;assert.equal(version,'v1');return network.promise})
 const a=f.sync.refresh(),b=f.sync.refresh()
 await new Promise(r=>setImmediate(r))
 assert.equal(f.shown[0].users[0].id,'1'); assert.equal(calls,1)
 network.resolve({version:'v1',unchanged:true}); await Promise.all([a,b])
 assert.equal(f.shown.length,1);assert.equal(f.saved.length,0)
})
test('a changed snapshot replaces removed contacts and errors retain visible local data',async()=>{
 let fail=false
 const f=await fixture(async()=>snapshot('v1',['1','2']),async()=>{if(fail)throw Error('offline');return {...snapshot('v2',['2']),unchanged:false}})
 await f.sync.refresh();assert.deepEqual(Array.from(f.shown.at(-1).users,u=>u.id),['2'])
 fail=true;await assert.rejects(()=>f.sync.refresh(true));assert.equal(f.shown.length,2)
 assert.equal(f.status.at(-1).phase,'stale')
})
test('logout discards late cache and network completions',async()=>{
 const disk=defer(),network=defer()
 const f=await fixture(()=>disk.promise,()=>network.promise)
 const pending=f.sync.refresh();f.renew();disk.resolve(snapshot('v1',['private']))
 network.resolve({...snapshot('v2',['private']),unchanged:false});await pending
 assert.equal(f.shown.length,0);assert.equal(f.saved.length,0)
})
test('a mutation during validation rejects the stale response and validates again',async()=>{
 const network=defer();let calls=0
 const f=await fixture(async()=>snapshot('v1',['1']),async()=>++calls===1?network.promise:{...snapshot('v3',['2']),unchanged:false})
 const pending=f.sync.refresh();await new Promise(r=>setImmediate(r));f.sync.invalidate()
 network.resolve({...snapshot('v2',['1']),unchanged:false});await pending
 assert.equal(calls,2);assert.deepEqual(Array.from(f.shown.at(-1).users,u=>u.id),['2'])
})

test('an expiring signed avatar refreshes the snapshot even when profile revisions did not change',async()=>{
 const local=snapshot('v1',['1']);local.users[0].avatarUrl='https://fixture.invalid/avatar?Expires=1'
 const f=await fixture(async()=>local,async version=>{
  assert.equal(version,'');return {...snapshot('v1',['1']),unchanged:false}
 })
 await f.sync.refresh();assert.equal(f.saved.length,1)
})

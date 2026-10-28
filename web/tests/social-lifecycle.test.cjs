const { test }=require('node:test')
const assert=require('node:assert/strict')
const path=require('node:path')
const { loadTs }=require('./helpers.cjs')
const source=path.resolve(__dirname,'../src/composables/useEncounter.ts')
const deferred=()=>{let resolve;const promise=new Promise(done=>{resolve=done});return {promise,resolve}}
async function fixture(api){
  const hooks={mount:[],dispose:[]}
  const events=new Map(),document={visibilityState:'visible',addEventListener:(key,fn)=>events.set(key,fn),removeEventListener:key=>events.delete(key)},navigator={onLine:true},window={addEventListener:(key,fn)=>events.set(key,fn),removeEventListener:key=>events.delete(key)}
  const vue={ref:value=>({value}),onMounted:work=>hooks.mount.push(work),onBeforeUnmount:work=>hooks.dispose.push(work),watch:()=>{}}
  const {exports:module}=await loadTs(source,{AbortController,document,navigator,window,setTimeout:()=>1,clearTimeout:()=>{}},{vue,'../store':{default:{state:{token:'fixture',accountId:'A'}}},'../api/social':api})
  const state=module.useEncounter();return {state,hooks,document,navigator,events}
}
test('late poll cannot regress a room after a successful action',async()=>{
  const stale=deferred()
  const {state,hooks}=await fixture({getEncounter:()=>stale.promise,actEncounter:async()=>({state:'ACTIVE',roomId:'room',messages:[]})})
  hooks.mount[0]()
  await state.action({action:'ready'})
  stale.resolve({state:'PREPARING',roomId:'room',messages:[]})
  await new Promise(resolve=>setImmediate(resolve))
  assert.equal(state.room.value.state,'ACTIVE');hooks.dispose[0]()
})
test('blocking clears previously merged room messages',async()=>{
  const {state,hooks}=await fixture({getEncounter:async()=>({state:'ACTIVE',roomId:'room',messages:[{seq:1,content:'fixture'}]}),actEncounter:async()=>({state:'ENDED',roomId:'room',reason:'BLOCKED',messages:[]})})
  hooks.mount[0]();await new Promise(resolve=>setImmediate(resolve))
  assert.equal(state.room.value.messages.length,1)
  await state.action({action:'leave'})
  assert.equal(state.room.value.messages.length,0);hooks.dispose[0]()
})
test('closing a room view aborts its request and ignores late content',async()=>{
  const late=deferred();let signal
  const {state,hooks}=await fixture({getEncounter:(_after,cancel)=>{signal=cancel;return late.promise},actEncounter:async()=>({state:'IDLE',messages:[]})})
  hooks.mount[0]();hooks.dispose[0]();assert.equal(signal.aborted,true)
  late.resolve({state:'ACTIVE',roomId:'old-account',messages:[{seq:1,content:'old'}]})
  await new Promise(resolve=>setImmediate(resolve))
  assert.equal(state.room.value.state,'IDLE');assert.equal(state.room.value.messages.length,0)
})

test('hidden views stop polling and foreground immediately refreshes without overlapping requests',async()=>{
 let calls=0
 const f=await fixture({getEncounter:async()=>{calls++;return {state:'IDLE',messages:[]}},actEncounter:async()=>({state:'IDLE',messages:[]})})
 f.document.visibilityState='hidden';f.hooks.mount[0]();assert.equal(calls,0)
 f.document.visibilityState='visible';f.events.get('visibilitychange')()
 await new Promise(resolve=>setImmediate(resolve));assert.equal(calls,1)
 f.document.visibilityState='hidden';f.events.get('visibilitychange')()
 f.navigator.onLine=false;f.document.visibilityState='visible';f.events.get('online')();assert.equal(calls,1)
 f.navigator.onLine=true;f.events.get('online')();await new Promise(resolve=>setImmediate(resolve));assert.equal(calls,2)
 f.hooks.dispose[0]();assert.equal(f.events.size,0)
})

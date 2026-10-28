const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {loadTs}=require('./helpers.cjs')
const file=path.resolve(__dirname,'../src/services/socialViewCache.ts')
test('social snapshots survive view remounts, expire without sliding TTL, and are detached from UI mutations',async()=>{
  let now=100000
  const {exports:{SocialViewCache}}=await loadTs(file)
  const cache=new SocialViewCache(()=>now),token=cache.activate('A',1)
  const page={users:[{id:'2',username:'fixture'}],nextCursor:30}
  cache.put(token,'search','people',page);cache.position(token,'search',120)
  page.users[0].username='changed'
  const cached=cache.get(cache.activate('A',1),'search','people')
  assert.equal(cached.data.users[0].username,'fixture');assert.equal(cached.scrollTop,120)
  cached.data.users[0].username='also changed'
  now+=19999;assert.equal(cache.get(token,'search','people').data.users[0].username,'fixture')
  now++;assert.equal(cache.get(token,'search','people'),undefined)
})
test('late reads cannot repopulate a mutated or new account/session cache; budgets evict old pages',async()=>{
  const {exports:{SocialViewCache}}=await loadTs(file)
  const cache=new SocialViewCache(()=>100000,2,2000),old=cache.activate('A',1)
  cache.put(old,'1','people',{users:[],nextCursor:null});cache.invalidate()
  assert.equal(cache.put(old,'late','people',{users:[],nextCursor:null}),false)
  const current=cache.capture()
  assert.equal(cache.put(current,'huge','people',{users:[{id:'x',bio:'x'.repeat(3000)}],nextCursor:null}),false)
  cache.rememberFilters(current,{q:'fixture',tag:'photo',channel:'all'})
  assert.equal(cache.filters(cache.activate('A',1)).q,'fixture')
  for(const key of ['1','2','3'])cache.put(current,key,'people',{users:[],nextCursor:null})
  assert.equal(cache.get(current,'1','people'),undefined)
  cache.activate('B',2);assert.equal(cache.get(current,'3','people'),undefined)
  assert.equal(cache.filters(cache.capture()).q,'')
  assert.equal(cache.put(current,'old','people',{users:[],nextCursor:null}),false)
  cache.activate('A',3);assert.equal(cache.get(cache.capture(),'3','people'),undefined)
})
test('a daily page never survives a Beijing midnight, even within its TTL',async()=>{
  let now=Date.parse('2026-10-28T15:59:59Z')
  const {exports:{SocialViewCache}}=await loadTs(file)
  const cache=new SocialViewCache(()=>now),token=cache.activate('A',1)
  cache.put(token,'daily','daily',{day:'2026-10-28',answers:[]})
  now+=2000;assert.equal(cache.get(token,'daily','daily'),undefined)
})

const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {loadTs}=require('./helpers.cjs')
test('recent views evict by recency and row budget without truncating another retained page',async()=>{
 const {exports:{ConversationViews}}=await loadTs(path.resolve(__dirname,'../src/services/conversationViews.ts'))
 const cache=new ConversationViews(2,3),view=id=>({messages:[{messageId:id,text:id}],before:null,hasMore:false})
 cache.put('1',view('1'));cache.put('2',view('2'));cache.get('1');cache.put('3',view('3'))
 assert.equal(cache.get('2'),undefined);assert.equal(cache.get('1').messages[0].messageId,'1')
 cache.put('4',{...view('4'),messages:[...view('4').messages,...view('5').messages,...view('6').messages]})
 assert.equal(cache.get('1'),undefined);assert.equal(cache.get('4').messages.length,3)
})
test('reading positions survive layout remounts and are explicitly cleared at session boundaries',async()=>{
 const {exports:{ConversationViews}}=await loadTs(path.resolve(__dirname,'../src/services/conversationViews.ts'))
 const cache=new ConversationViews(),position={key:'message:0',delta:13,bottom:false}
 cache.savePosition('A:1',position);assert.equal(cache.position('A:1').delta,13);assert.equal(cache.position('B:1'),undefined)
 cache.clear();assert.equal(cache.position('A:1'),undefined)
})

test('updating one view never recounts unrelated message text and batch updates enforce budgets once',async()=>{
 const {exports:{ConversationViews}}=await loadTs(path.resolve(__dirname,'../src/services/conversationViews.ts'))
 const cache=new ConversationViews(10,100,20);let reads=0
 cache.put('a',{messages:[{get text(){reads++;return 'aaaa'},messageId:'a'}],before:null,hasMore:false})
 reads=0
 cache.put('b',{messages:[{text:'bb',messageId:'b'}],before:null,hasMore:false})
 assert.equal(reads,0)
 cache.updateAll(rows=>rows[0].messageId==='b'?[{text:'bbbb',messageId:'b'}]:rows)
 assert.equal(reads,0)
 cache.update('b',()=>[{text:'x'.repeat(21)}]);assert.equal(cache.get('b'),undefined)
 assert.equal(cache.get('a'),undefined)
 cache.clear();cache.put('c',{messages:[{text:'ok'}],before:null,hasMore:false});assert.ok(cache.get('c'))
})

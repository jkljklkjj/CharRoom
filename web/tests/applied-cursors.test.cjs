const test=require('node:test'),assert=require('node:assert/strict'),path=require('node:path')
const {IDBFactory,IDBKeyRange,IDBObjectStore}=require('fake-indexeddb')
const {loadTs}=require('./helpers.cjs')
const fixture=async()=> (await loadTs(path.resolve(__dirname,'../src/utils/messageDB.ts'),{indexedDB:new IDBFactory(),IDBKeyRange},
 {'./format':{conversationPreviewText:row=>row.text}})).exports
const row={messageId:'17',user:'2',text:'fixture',time:1,seqId:'17'}
test('history and its applied cursor commit together and remain account scoped',async()=>{
 const db=await fixture()
 await db.mergeHistoryMessages('1','2',[row],{conversationId:'1:2',seqId:'17'})
 assert.equal((await db.getMessages('1','2')).length,1)
 assert.equal((await db.getAppliedCursors('1'))['1:2'],'17')
 assert.deepEqual({...await db.getAppliedCursors('3')},{})
 await db.mergeHistoryMessages('1','2',[{...row,seqId:'9'}],{conversationId:'1:2',seqId:'9'})
 assert.equal((await db.getAppliedCursors('1'))['1:2'],'17')
})
test('aborting the cursor write rolls back message writes as well',async()=>{
 const db=await fixture(),original=IDBObjectStore.prototype.put
 try {
  IDBObjectStore.prototype.put=function(...args){const result=original.apply(this,args);if(this.name==='sync_cursors')this.transaction.abort();return result}
  await assert.rejects(()=>db.mergeHistoryMessages('1','2',[row],{conversationId:'1:2',seqId:'17'}))
 }finally{IDBObjectStore.prototype.put=original}
 assert.equal((await db.getMessages('1','2')).length,0)
 assert.deepEqual({...await db.getAppliedCursors('1')},{})
})

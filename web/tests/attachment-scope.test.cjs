const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), vm = require('node:vm')
const { transpile, loadTs } = require('./helpers.cjs')
function fixture() {
 const source = fs.readFileSync(path.resolve(__dirname, '../src/components/ChatWindow.vue'), 'utf8')
 const start = source.indexOf('async function handleFileUpload('), end = source.indexOf('// 显示消息上下文菜单', start)
 let finish
 const uploaded = new Promise(resolve => { finish = resolve })
 const sent = [], saved = [], errors = [], session = new AbortController()
 let current = true
 const context = { currentChatId: { value: 'user:42' }, isGroupChat: { value: false },
   store: { state: { accountId: '1' }, addMessage: async m => saved.push(m), addGroupMessage: async m => saved.push(m), updateMessageStatus() {} },
   draftKey: () => '1:' + context.currentChatId.value, sessionEpoch: { capture: () => ({ signal: session.signal, isCurrent: () => current }) },
   uploads: new Map(), uploading: { value: false }, MAX_FILE_SIZE: 10485760, AbortController,
   uploadAttachment: () => uploaded, globalThis: { crypto: { randomUUID: () => 'fixture' } },
   chatTargetId: key => key.split(':')[1], SELF_USER: 'you', AGENT_USER_ID: 0, MSG_TYPE: { CHAT: 3, GROUP_CHAT: 4, AGENT_CHAT: 5 },
   chatSocket: { enqueueWrapper: async wrapper => sent.push(wrapper) },
   submitDraft: async (persist, enqueue, commit) => { await persist(); await enqueue(); commit() },
   toast: { error: key => errors.push(key) }, t: key => key, scrollToBottom() {}, Date }
 vm.runInNewContext(transpile(source.slice(start,end) + '\nglobalThis.upload = handleFileUpload'), context)
 return { context, finish, sent, saved, errors, logout: () => { current = false; session.abort() } }
}
test('attachment completion retains the original account, target, and chat type after switching chats', async () => {
 const f = fixture(), sending = f.context.globalThis.upload({ name: 'fixture.png', type: 'image/png', size: 1024 })
 f.context.currentChatId.value = 'group:99'; f.context.isGroupChat.value = true
 f.finish('https://fixture.invalid/file.png'); await sending
 assert.equal(f.sent.length, 1)
 assert.equal(f.sent[0].type, 3)
 assert.equal(f.sent[0].chat.targetClientId, '42')
 assert.equal(f.sent[0].chat.fileUrl, 'https://fixture.invalid/file.png')
 assert.equal(f.saved[0].targetId, '42')
 assert.ok(!JSON.stringify(f.sent).includes('base64'))
})
test('logout and cancellation discard late upload completions', async () => {
 for (const logout of [true,false]) {
  const f=fixture(), sending=f.context.globalThis.upload({ name:'fixture', type:'text/plain', size:12 })
  if(logout) f.logout(); else [...f.context.uploads.values()][0].abort()
  f.finish('https://fixture.invalid/file'); await sending
  assert.equal(f.sent.length,0); assert.equal(f.saved.length,0)
 }
})
test('attachment API accepts the backend success code and sends bytes as multipart form data', async () => {
 let request
 const { exports: api } = await loadTs(path.resolve(__dirname,'../src/api/attachments.ts'), { FormData }, {
  './http': { API_BASE:'http://fixture.invalid/api', safeFetch: async (url,opts) => { request={url,opts}; return { ok:true,body:{code:0,data:'https://fixture.invalid/file'} } } }
 })
 const result=await api.uploadAttachment(new Blob(['fixture']), new AbortController().signal)
 assert.equal(result,'https://fixture.invalid/file')
 assert.equal(request.url,'http://fixture.invalid/api/files')
 assert.equal(request.opts.method,'POST')
 assert.ok(request.opts.body instanceof FormData)
})

test('quota rejection keeps its business code for the composer and never returns a URL', async () => {
 const { exports: api } = await loadTs(path.resolve(__dirname,'../src/api/attachments.ts'), { FormData }, {
  './http': { API_BASE:'http://fixture.invalid/api', safeFetch: async () => ({ok:false,body:{code:5002,message:'Daily quota reached'}}) }
 })
 await assert.rejects(() => api.uploadAttachment(new Blob(['fixture']), new AbortController().signal), error => error.code === 5002)
})

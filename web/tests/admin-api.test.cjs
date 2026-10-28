const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')
function storage() {
  const values = new Map()
  return { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
async function fixture(fetch) {
  const sessionStorage = storage()
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/admin.ts'), { fetch, sessionStorage }, {
    '../utils/device': { getDeviceId: () => 'fixture', getDeviceType: () => 'web' }
  })
  return { api: new api.AdminApi(), sessionStorage }
}
const response = (data, status = 200) => Promise.resolve({ ok: status === 200, status, json: async () => ({ code: status === 200 ? 0 : status, data, message: 'Denied' }) })
test('administrator login checks server permission and never retains the password', async () => {
  const seen = []
  const { api, sessionStorage } = await fixture((url, options) => {
    seen.push([url, options])
    return url.endsWith('/auth/login') ? response({ accessToken:'admin', refreshToken:'refresh' }) : response({ userId:42 })
  })
  assert.equal((await api.login('42', 'password')).userId, 42)
  assert.equal(seen[1][1].headers.Authorization, 'Bearer admin')
  assert.ok(seen.every(([, options]) => options.cache === 'no-store'))
  assert.equal(sessionStorage.getItem('charroom_admin_access'), 'admin')
  assert.equal(sessionStorage.getItem('password'), null)
  api.logout()
  assert.equal(sessionStorage.getItem('charroom_admin_access'), null)
})
test('a normal account cannot retain a management session', async () => {
  const { api, sessionStorage } = await fixture(url => url.endsWith('/auth/login') ? response({ accessToken:'ordinary', refreshToken:'refresh' }) : response(null, 403))
  await assert.rejects(api.login('7', 'password'), error => error.status===403)
  assert.equal(api.hasSession, false)
  assert.equal(sessionStorage.getItem('charroom_admin_refresh'), null)
})
test('concurrent unauthorized requests share a single refresh', async () => {
  let refreshes = 0
  const { api } = await fixture((url, options) => {
    if (url.endsWith('/auth/login')) return response({ accessToken:'old', refreshToken:'refresh' })
    if (url.endsWith('/admin/me')) return response({ userId:42 })
    if (url.endsWith('/auth/refresh')) { refreshes++; return response({ accessToken:'new', refreshToken:'next' }) }
    return options.headers.Authorization==='Bearer old' ? response(null,401) : response({ items:[], hasMore:false, active:null })
  })
  await api.login('42','password')
  await Promise.all([api.list('android',0), api.list('desktop',0)])
  assert.equal(refreshes,1)
})
test('logout invalidates a late login response', async () => {
  let finish
  const { api, sessionStorage } = await fixture(() => new Promise(resolve => { finish=resolve }))
  const pending=api.login('42','password')
  api.logout()
  finish(await response({ accessToken:'late', refreshToken:'late' }))
  await assert.rejects(pending)
  assert.equal(api.hasSession,false)
  assert.equal(sessionStorage.getItem('charroom_admin_access'),null)
})

test('a previous login permission failure cannot clear a newer administrator session', async () => {
  let finishOld, loginCount=0
  const { api, sessionStorage }=await fixture((url,options)=>{
    if(url.endsWith('/auth/login'))return response({accessToken:++loginCount===1?'old':'new',refreshToken:'refresh'})
    if(options.headers.Authorization==='Bearer old')return new Promise(resolve=>{finishOld=resolve})
    return response({userId:42})
  })
  const old=api.login('42','password')
  const rejected=assert.rejects(old)
  while(!finishOld)await new Promise(resolve=>setImmediate(resolve))
  await api.login('42','password')
  finishOld(await response(null,403))
  await rejected
  assert.equal(api.hasSession,true)
  assert.equal(sessionStorage.getItem('charroom_admin_access'),'new')
})
test('editing and publishing carry the selected revision and target', async () => {
  const seen=[]
  const { api }=await fixture((url, options) => { seen.push([url,options]); return response({}) })
  const row={id:'release',revision:7}, input={platform:'android',version:'2.0.0',minSupportedVersion:'1.0.0',forceUpdate:false,downloadUrl:'https://example.com/app.apk',releaseNotes:'Fixes'}
  await api.edit(row,input)
  await api.transition(row,'publish')
  assert.ok(seen[0][0].endsWith('/admin/releases/release?revision=7'))
  assert.equal(seen[0][1].method,'PUT')
  assert.deepEqual(JSON.parse(seen[1][1].body),{revision:7})
})

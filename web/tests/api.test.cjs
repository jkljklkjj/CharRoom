const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')
function fixtureStore() {
  return { state: { token: 'expired', refreshToken: 'refresh-A', accountId: 'A' },
    setToken(value) { this.state.token = value }, setRefreshToken(value) { this.state.refreshToken = value },
    setAccountId(value) { this.state.accountId = value }, setLoginValid() {}, clearAll() {} }
}
const response = (status, data) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
const storage = () => { const data = new Map(); return { setItem: (k, v) => data.set(k, v), getItem: k => data.get(k), removeItem: k => data.delete(k) } }
test('concurrent 401 requests refresh once and retry with the new bearer token', async () => {
  const store = fixtureStore(), sessionStorage = storage()
  let refreshCalls = 0
  const fetch = async (url, options) => {
    if (url.endsWith('/auth/refresh')) {
      refreshCalls++
      await new Promise(resolve => setTimeout(resolve, 10))
      return response(200, { data: { accessToken: 'new-A', refreshToken: 'refresh-new-A' } })
    }
    return options.headers.get('Authorization') === 'Bearer new-A' ? response(200, { data: { id: 'A' } }) : response(401, {})
  }
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/http.ts'), {
    fetch, Headers, sessionStorage, localStorage: storage(), window: { location: {} }
  }, { '../store': { default: store } })
  const results = await Promise.all([api.safeFetch('/one'), api.safeFetch('/two')])
  assert.equal(refreshCalls, 1)
  assert.ok(results.every(result => result.ok && result.body.data.id === 'A'))
  assert.equal(sessionStorage.getItem('charroom_token'), 'new-A')
})
test('failed token refresh settles every waiting request without recursive refresh', async () => {
  const store = fixtureStore()
  let refreshCalls = 0
  const fetch = async url => {
    if (url.endsWith('/auth/refresh')) {
      refreshCalls++
      await new Promise(resolve => setTimeout(resolve, 10))
    }
    return response(401, { message: 'fixture rejection' })
  }
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/http.ts'), {
    fetch, Headers, sessionStorage: storage(), localStorage: storage(), window: { location: {} }
  }, { '../store': { default: store } })
  const results = await Promise.race([
    Promise.all([api.safeFetch('/one'), api.safeFetch('/two')]),
    new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('refresh waiters hung')), 500); timer.unref() })
  ])
  assert.equal(refreshCalls, 1)
  assert.ok(results.every(result => !result.ok && [0, 401].includes(result.status)))
  assert.ok(results.some(result => result.status === 401))
})
test('API default export supplies quota, payment status and friend notification methods', async () => {
  const store = fixtureStore()
  const { exports: module } = await loadTs(path.resolve(__dirname, '../src/api/index.ts'), {
    fetch: async () => response(200, { data: { balanceFen: 250 } }), Headers,
    sessionStorage: storage(), localStorage: storage(), window: { location: {} }
  }, { '../store': { default: store } })
  // 支付改只读查单：客户端不再提供 confirmPurchase（入账由服务端确认）
  for (const name of ['getTokenQuota', 'getTokenPrices', 'purchaseTokens', 'getPurchaseStatus', 'getFriendNotifications', 'markFriendNotificationsRead', 'getOnlineStatus']) {
    assert.equal(typeof module.default[name], 'function', name)
  }
  assert.equal(module.default.confirmPurchase, undefined)
  // 客服 AI 副驾 API re-export
  for (const name of ['getCopilotMe', 'listCopilotConversations', 'sendCopilotMessage', 'listCopilotChannels']) {
    assert.equal(typeof module.default[name], 'function', name)
  }
  assert.equal((await module.default.getTokenQuota()).balanceFen, 250)
})


test('late refresh after account switch cannot replace the new account credentials', async () => {
  const store = fixtureStore(), sessionStorage = storage(), localStorage = storage()
  let release, requested
  const gate = new Promise(resolve => { release = resolve })
  const started = new Promise(resolve => { requested = resolve })
  const fetch = async url => {
    if (url.endsWith('/auth/refresh')) { requested(); await gate; return response(200, { data: { accessToken: 'late-A', refreshToken: 'late-refresh-A' } }) }
    return response(401, {})
  }
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/http.ts'), {
    fetch, Headers, sessionStorage, localStorage, window: { location: {} }
  }, { '../store': { default: store } })
  const pending = api.safeFetch('/old-account')
  await started
  store.state.accountId = 'B'; store.state.token = 'token-B'; store.state.refreshToken = 'refresh-B'
  release()
  assert.equal((await pending).ok, false)
  assert.equal(store.state.token, 'token-B')
  assert.equal(store.state.refreshToken, 'refresh-B')
  assert.equal(sessionStorage.getItem('charroom_token'), undefined)
})

test('REST and realtime rejection share one refresh; transient outages preserve credentials', async () => {
  const store = fixtureStore(), saved = storage(); let calls = 0, unavailable = true
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/http.ts'), {
    Headers, sessionStorage: saved, localStorage: storage(), window: { location: {} },
    fetch: async url => {
      if (!url.endsWith('/auth/refresh')) return response(200, { code: 1006 })
      calls++; await new Promise(resolve => setTimeout(resolve, 10))
      return unavailable ? response(503, {}) : response(200, { code: 0, data: { accessToken: 'new-A', refreshToken: 'new-refresh' } })
    }
  }, { '../store': { default: store } })
  await Promise.all([api.safeFetch('/expired'), api.refreshSession('expired')])
  assert.equal(calls, 1); assert.equal(store.state.token, 'expired'); assert.equal(store.state.accountId, 'A')
  unavailable = false
  assert.equal(await api.refreshSession('expired'), 'ready')
  assert.equal(store.state.token, 'new-A')
  assert.equal(await api.refreshSession('expired'), 'ready', 'late realtime rejection of the old token reuses the rotated one')
  assert.equal(calls, 2, 'late rejection must not rotate the fresh token again')
})

test('a second 401 after rotation clears the rejected session without another rotation', async () => {
  const store = fixtureStore(); let rotations = 0
  const { exports: api } = await loadTs(path.resolve(__dirname, '../src/api/http.ts'), {
    Headers, sessionStorage: storage(), localStorage: storage(), window: { location: {} },
    fetch: async url => url.endsWith('/auth/refresh') ? (rotations++, response(200, { data: { accessToken: 'new-A', refreshToken: 'new-refresh' } })) : response(401, {})
  }, { '../store': { default: store } })
  assert.equal((await api.safeFetch('/revoked')).status, 401)
  assert.equal(rotations, 1); assert.equal(store.state.token, '')
})

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
  assert.ok(results.every(result => !result.ok && result.status === 401))
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

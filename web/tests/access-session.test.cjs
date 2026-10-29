const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const jwt = claims => 'fixture.' + Buffer.from(JSON.stringify(claims)).toString('base64url') + '.signature'
const load = () => loadTs(path.resolve(__dirname, '../src/services/accessSession.ts'), { atob })
test('local token expiry is bound to the JWT account and exp, not a separate cached date', async () => {
  const { exports: { readAccessSession } } = await load()
  const value = readAccessSession(jwt({ sub: '42', uid: 42, exp: 2000 }))
  assert.equal(value.accountId, '42'); assert.equal(value.expiresAt, 2000000)
  for (const token of ['', 'opaque', jwt({ uid: 42 }), jwt({ uid: 42, exp: '2000' }), jwt({ uid: 0, exp: 2000 }), jwt({ uid: 42, sub: '43', exp: 2000 })]) assert.equal(readAccessSession(token), null)
})
test('expiry boundary never treats an expired access token as usable', async () => {
  const { exports: { hasUsableAccessToken } } = await load()
  const token = jwt({ uid: 42, exp: 2000 })
  assert.equal(hasUsableAccessToken(token, 1999999), true)
  assert.equal(hasUsableAccessToken(token, 2000000), false)
})

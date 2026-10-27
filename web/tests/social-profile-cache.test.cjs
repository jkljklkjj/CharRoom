const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')

function localStorageStub() {
  const map = new Map()
  return {
    getItem: key => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => { map.set(key, String(value)) },
    removeItem: key => { map.delete(key) }
  }
}

// “我的名片”本地缓存：只缓存本人资料、按账号隔离
test('own profile cache round-trips only SELF profiles of the same account', async () => {
  const { exports: cache } = await loadTs(path.resolve(__dirname, '../src/utils/socialProfileCache.ts'), {
    localStorage: localStorageStub()
  })
  const profile = { id: '42', username: 'me', relation: 'SELF', detailsVisible: true }
  // 非本人资料不写缓存
  cache.saveOwnProfileCache('42', { ...profile, relation: 'FRIEND' })
  assert.equal(cache.loadOwnProfileCache('42'), null)
  cache.saveOwnProfileCache('42', profile)
  // loadTs 在独立 realm 中运行，直接 deepEqual 会因原型不同误报，用 JSON 对比内容
  assert.equal(JSON.stringify(cache.loadOwnProfileCache('42')), JSON.stringify(profile))
  // 账号隔离：换个账号读不到
  assert.equal(cache.loadOwnProfileCache('43'), null)
  cache.clearOwnProfileCache('42')
  assert.equal(cache.loadOwnProfileCache('42'), null)
})

test('cache rejects corrupt data, foreign profiles and other versions', async () => {
  const store = localStorageStub()
  const { exports: cache } = await loadTs(path.resolve(__dirname, '../src/utils/socialProfileCache.ts'), {
    localStorage: store
  })
  store.setItem('charroom_social_profile_7', '{broken json')
  assert.equal(cache.loadOwnProfileCache('7'), null)
  store.setItem('charroom_social_profile_7', JSON.stringify({ version: 99, profile: { id: '7', relation: 'SELF' } }))
  assert.equal(cache.loadOwnProfileCache('7'), null)
  // 串号数据（键是 7，资料属于 9）不生效
  store.setItem('charroom_social_profile_7', JSON.stringify({ version: 1, profile: { id: '9', relation: 'SELF' } }))
  assert.equal(cache.loadOwnProfileCache('7'), null)
  assert.equal(cache.loadOwnProfileCache(''), null)
})

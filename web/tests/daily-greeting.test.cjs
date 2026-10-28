const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
const file = path.resolve(__dirname, '../src/utils/dailyGreeting.ts')
const KEY = 'charroom.directory-greeting'
const DAY = 24 * 60 * 60 * 1000

function memoryStorage(initial) {
  const map = new Map()
  if (initial !== undefined) map.set(KEY, initial)
  return { getItem: k => (map.has(k) ? map.get(k) : null), setItem: (k, v) => map.set(k, v), map }
}

test('greeting stays stable within the same day and is persisted', async () => {
  const { exports: { pickDailyGreeting } } = await loadTs(file)
  const storage = memoryStorage(null)
  const now = new Date(2026, 0, 5, 10, 0, 0).getTime()

  const first = pickDailyGreeting({ storage, now, random: () => 0 })
  const second = pickDailyGreeting({ storage, now: now + 60 * 60 * 1000, random: () => 0.9 })

  assert.equal(first, second, '同一天刷新不应换文案')
  assert.equal(JSON.parse(storage.map.get(KEY)).length, 1)
})

test('greeting never repeats an index used in the previous three days', async () => {
  const { exports: { pickDailyGreeting } } = await loadTs(file)
  const storage = memoryStorage(null)
  const day1 = new Date(2026, 0, 5, 9, 0, 0).getTime()
  const seen = []

  for (let offset = 0; offset < 3; offset++) {
    const index = pickDailyGreeting({ storage, now: day1 + offset * DAY, random: () => 0 })
    assert.ok(!seen.includes(index), `第 ${offset + 1} 天不应重复：${index}`)
    seen.push(index)
  }
  assert.deepEqual(seen, [0, 1, 2], '随机源固定为 0 时按排除顺序取下一个可用下标')
})

test('an index becomes available again once it is older than three days', async () => {
  const { exports: { pickDailyGreeting } } = await loadTs(file)
  const storage = memoryStorage(null)
  const day1 = new Date(2026, 0, 5, 9, 0, 0).getTime()

  pickDailyGreeting({ storage, now: day1, random: () => 0 })          // 0
  pickDailyGreeting({ storage, now: day1 + DAY, random: () => 0 })     // 1
  pickDailyGreeting({ storage, now: day1 + 2 * DAY, random: () => 0 }) // 2
  const fourth = pickDailyGreeting({ storage, now: day1 + 3 * DAY, random: () => 0 })

  assert.equal(fourth, 0, '第 1 天的文案到第 4 天已超出三天窗口，可以再次出现')
  const history = JSON.parse(storage.map.get(KEY))
  assert.ok(history.length <= 3, '历史只保留最近三天')
})

test('falls back to a random index when storage is unavailable or corrupted', async () => {
  const { exports: { pickDailyGreeting } } = await loadTs(file)
  const now = new Date(2026, 0, 5, 9, 0, 0).getTime()

  const withoutStorage = pickDailyGreeting({ storage: null, now, random: () => 0.5 })
  assert.ok(withoutStorage >= 0 && withoutStorage < 10)

  const corrupted = memoryStorage('{not json')
  const fromCorrupted = pickDailyGreeting({ storage: corrupted, now, random: () => 0.5 })
  assert.ok(fromCorrupted >= 0 && fromCorrupted < 10)
  assert.equal(JSON.parse(corrupted.map.get(KEY)).length, 1, '损坏的历史会被重建')
})

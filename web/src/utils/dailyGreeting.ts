/**
 * 「目录问候语」每日轮换：10 条文案里每天随机挑一条，且三天内不重复。
 *
 * 纯函数 + 可注入 storage/now/random，便于在 node 测试里固定时间与随机源，
 * 不依赖浏览器全局（预渲染/SSR 环境下 storage 可能不存在）。
 */

export const GREETING_COUNT = 10

const STORAGE_KEY = 'charroom.directory-greeting'
const KEEP_DAYS = 3
const DAY_MS = 24 * 60 * 60 * 1000

export interface GreetingStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

interface GreetingEntry {
  index: number
  day: string
}

export interface PickOptions {
  storage?: GreetingStorage | null
  now?: number
  random?: () => number
}

/** 本地时区的 YYYY-MM-DD（跨天按用户本地时间算，而不是 UTC） */
export function dayKey(timestamp: number): string {
  const date = new Date(timestamp)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function resolveStorage(explicit?: GreetingStorage | null): GreetingStorage | null {
  if (explicit !== undefined) return explicit
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

function readHistory(storage: GreetingStorage | null): GreetingEntry[] {
  if (!storage) return []
  try {
    const parsed = JSON.parse(storage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.filter((entry): entry is GreetingEntry =>
      entry && Number.isInteger(entry.index) && entry.index >= 0 && entry.index < GREETING_COUNT &&
      typeof entry.day === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(entry.day))
  } catch {
    return []
  }
}

function writeHistory(storage: GreetingStorage | null, history: GreetingEntry[]): void {
  if (!storage) return
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(history))
  } catch {
    // 隐私模式/配额满：退化为每次随机，不影响页面
  }
}

/** 三天内出现过的日 key（含今天） */
function recentDays(now: number): Set<string> {
  const days = new Set<string>()
  for (let offset = 0; offset < KEEP_DAYS; offset++) {
    days.add(dayKey(now - offset * DAY_MS))
  }
  return days
}

/**
 * 选出今天要展示的问候语下标。
 * - 同一天重复调用返回同一个值（刷新页面不会换）；
 * - 从「最近三天未出现过」的下标里随机挑；
 * - 若 10 条都在三天内出现过（不可能，但兜底），退回全量随机。
 */
export function pickDailyGreeting(options: PickOptions = {}): number {
  const now = options.now ?? Date.now()
  const random = options.random ?? Math.random
  const storage = resolveStorage(options.storage)
  const today = dayKey(now)

  const history = readHistory(storage)
  const todays = history.find(entry => entry.day === today)
  if (todays) return todays.index

  const recent = recentDays(now)
  const blocked = new Set(history.filter(entry => recent.has(entry.day)).map(entry => entry.index))
  const pool = Array.from({ length: GREETING_COUNT }, (_, index) => index).filter(index => !blocked.has(index))
  const candidates = pool.length ? pool : Array.from({ length: GREETING_COUNT }, (_, index) => index)
  const index = candidates[Math.min(candidates.length - 1, Math.floor(random() * candidates.length))]

  writeHistory(storage, [...history.filter(entry => recent.has(entry.day)), { index, day: today }])
  return index
}
